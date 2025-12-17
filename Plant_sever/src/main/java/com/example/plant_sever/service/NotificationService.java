package com.example.plant_sever.service;

import com.example.plant_sever.DAO.DeviceTokenRepo;
import com.example.plant_sever.DAO.GardenScheduleRepo;
import com.example.plant_sever.DAO.NotificationRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.NotificationResponse;
import com.example.plant_sever.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final DeviceTokenRepo deviceTokenRepo;
    private final GardenScheduleRepo gardenScheduleRepo;
    private final NotificationRepo notificationRepo;
    private final UserRepo userRepo; // ✅ thêm giống GardenService
    private final HttpClient httpClient;

    @Value("${fcm.server.key:}")
    private String fcmServerKey;

    public NotificationService(
            DeviceTokenRepo deviceTokenRepo,
            GardenScheduleRepo gardenScheduleRepo,
            NotificationRepo notificationRepo,
            UserRepo userRepo
    ) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.gardenScheduleRepo = gardenScheduleRepo;
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ================================================================
    // 🔒 UTILITY (giống GardenService)
    // ================================================================
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        // GardenService của bạn đang cast thẳng String.
        // Mình để an toàn hơn: nếu principal không phải String thì fallback toString()
        String username = (principal instanceof String)
                ? (String) principal
                : principal.toString();

        return userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    // ================================================================
    // ✅ TOKEN register/update
    // ================================================================
    public void saveOrUpdateToken(Long userId, String token, String platform) {
        Optional<DeviceToken> existing = deviceTokenRepo.findByToken(token);
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setUserId(userId);
            dt.setPlatform(platform);
            deviceTokenRepo.save(dt);
        } else {
            DeviceToken dt = new DeviceToken(userId, token, platform);
            deviceTokenRepo.save(dt);
        }
    }

    // ✅ HƯỚNG B: FE chỉ gửi token+platform, BE tự lấy userId
    public void saveOrUpdateMyToken(String token, String platform) {
        Long userId = getCurrentUserId();
        saveOrUpdateToken(userId, token, platform);
    }

    // ================================================================
    // 🔔 SEND PUSH + SAVE DB
    // ================================================================
    public void sendToToken(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isBlank()) return;
        if (fcmServerKey == null || fcmServerKey.isBlank()) {
            log.warn("FCM server key is not configured; skipping push send");
            return;
        }
        String payload = buildSinglePayload(token, title, body, data);
        sendPayload(payload, List.of(token));
    }

    public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data, Long userId) {
        boolean hasTokens = tokens != null && !tokens.isEmpty();

        if (hasTokens) {
            if (fcmServerKey == null || fcmServerKey.isBlank()) {
                log.warn("FCM server key is not configured; skipping push send");
            } else {
                String payload = buildMulticastPayload(tokens, title, body, data);
                sendPayload(payload, tokens);
            }
        } else {
            log.info("No device tokens provided to send notification");
        }

        if (userId != null) {
            saveNotification(userId, title, body);
        }
    }

    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenRepo.findByUserId(userId)
                .stream()
                .map(DeviceToken::getToken)
                .toList();
        sendToTokens(tokens, title, body, data, userId);
    }

    // ✅ HƯỚNG B: gửi cho chính mình
    public void sendToMe(String title, String body, Map<String, String> data) {
        Long userId = getCurrentUserId();
        sendToUser(userId, title, body, data);
    }

    public void sendWelcomeNotification(Long userId, String username, boolean isNew) {
        String title = isNew ? "Chào mừng đến Plan4Plant" : "Chào mừng quay lại!";
        String body = isNew
                ? "Xin chào " + username + "! Chúng tôi sẽ đồng hành cùng bạn chăm sóc cây."
                : "Rất vui được gặp lại " + username + ". Kiểm tra lịch chăm cây hôm nay nhé!";
        sendToUser(userId, title, body, Map.of("type", "welcome"));
    }

    // ================================================================
    // ⏰ Scheduled reminder
    // ================================================================
    @Scheduled(fixedRate = 60_000)
    public void remindUpcomingPlans() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.plusMinutes(14);
        LocalDateTime end = now.plusMinutes(16);

        List<GardenSchedule> upcoming = gardenScheduleRepo.findByCompletionAndScheduledTimeBetween(
                Completion.NotDone, start, end);

        for (GardenSchedule schedule : upcoming) {
            if (schedule.getGarden() == null || schedule.getGarden().getUser() == null) continue;
            Long userId = schedule.getGarden().getUser().getId();
            if (userId == null) continue;

            String title = "Nhắc lịch chăm cây";
            String body = buildPlanBody(schedule);

            if (notificationRepo.existsByUserIdAndTitleAndBody(userId, title, body)) {
                continue;
            }

            sendToUser(userId, title, body, Map.of(
                    "type", "plan_reminder",
                    "scheduleId", String.valueOf(schedule.getId())
            ));
        }
    }

    private String buildPlanBody(GardenSchedule schedule) {
        String action = schedule.getType() != null ? schedule.getType().name() : "CHĂM SÓC";
        String timeStr = schedule.getScheduledTime() != null
                ? schedule.getScheduledTime().format(TIME_FORMATTER)
                : "sắp tới";
        String nickname = schedule.getGarden() != null ? schedule.getGarden().getNickname() : null;
        String plantName = schedule.getGarden() != null && schedule.getGarden().getPlant() != null
                ? schedule.getGarden().getPlant().getCommonName()
                : null;
        String subject = nickname != null ? nickname : plantName;
        if (subject != null) {
            return "Bạn có lịch " + action + " cho \"" + subject + "\" lúc " + timeStr + ".";
        }
        return "Bạn có lịch " + action + " lúc " + timeStr + ".";
    }

    // ================================================================
    // 🌐 FCM payload builders
    // ================================================================
    private String buildSinglePayload(String token, String title, String body, Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"to\":").append('"').append(escapeJson(token)).append('"');
        sb.append(",\"notification\":{\"title\":\"").append(escapeJson(title)).append("\",\"body\":\"")
                .append(escapeJson(body)).append("\"}");
        if (data != null && !data.isEmpty()) {
            sb.append(",\"data\":{");
            sb.append(data.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
                    .collect(Collectors.joining(",")));
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private String buildMulticastPayload(List<String> tokens, String title, String body, Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"registration_ids\":[");
        sb.append(tokens.stream().map(t -> '"' + escapeJson(t) + '"').collect(Collectors.joining(",")));
        sb.append(']');
        sb.append(",\"notification\":{\"title\":\"").append(escapeJson(title)).append("\",\"body\":\"")
                .append(escapeJson(body)).append("\"}");
        if (data != null && !data.isEmpty()) {
            sb.append(",\"data\":{");
            sb.append(data.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
                    .collect(Collectors.joining(",")));
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private void sendPayload(String payload, List<String> tokens) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://fcm.googleapis.com/fcm/send"))
                .header("Authorization", "key=" + fcmServerKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String body = resp.body();
            log.info("FCM send status={} response={}", status, body);

            if (body != null && (body.contains("NotRegistered") || body.contains("InvalidRegistration"))) {
                for (String t : tokens) {
                    deviceTokenRepo.findByToken(t).ifPresent(deviceTokenRepo::delete);
                }
            }

        } catch (IOException | InterruptedException e) {
            log.error("Failed to send FCM payload", e);
            Thread.currentThread().interrupt();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ================================================================
    // 💾 Save + Read
    // ================================================================
    public Notification saveNotification(Long userId, String title, String body) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .isRead(false)
                .build();
        return notificationRepo.save(notification);
    }

    // ✅ HƯỚNG B: save local cho chính mình
    public Notification saveMyNotification(String title, String body) {
        return saveNotification(getCurrentUserId(), title, body);
    }

    public List<NotificationResponse> getByUser(Long userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ✅ HƯỚNG B: get noti của mình
    public List<NotificationResponse> getMine() {
        return getByUser(getCurrentUserId());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepo.countByUserIdAndIsReadFalse(userId);
    }

    // ✅ HƯỚNG B: count của mình
    public long getMyUnreadCount() {
        return getUnreadCount(getCurrentUserId());
    }

    // ================================================================
    // ✅ Read/unread + delete (giữ hàm cũ, thêm hàm "mine")
    // ================================================================
    @Transactional
    public boolean updateReadFlag(Long notificationId, Long userId, boolean isRead) {
        Optional<Notification> found = notificationRepo.findById(notificationId);
        if (found.isEmpty()) return false;

        Notification notification = found.get();
        if (userId != null && !userId.equals(notification.getUserId())) {
            return false;
        }

        notificationRepo.updateReadFlag(notificationId, isRead);
        return true;
    }

    // ✅ HƯỚNG B: chỉ update nếu noti thuộc user hiện tại
    @Transactional
    public boolean updateReadFlagMine(Long notificationId, boolean isRead) {
        Long userId = getCurrentUserId();
        int updated = notificationRepo.updateReadFlagForUser(notificationId, userId, isRead);
        return updated > 0;
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        if (userId == null) return 0;
        return notificationRepo.markAllAsReadByUser(userId);
    }

    // ✅ HƯỚNG B
    @Transactional
    public int markAllAsReadMine() {
        return notificationRepo.markAllAsReadByUser(getCurrentUserId());
    }

    @Transactional
    public boolean delete(Long notificationId, Long userId) {
        if (userId == null) {
            if (!notificationRepo.existsById(notificationId)) return false;
            notificationRepo.deleteById(notificationId);
            return true;
        }
        int deleted = notificationRepo.deleteByIdAndUserId(notificationId, userId);
        return deleted > 0;
    }

    // ✅ HƯỚNG B
    @Transactional
    public boolean deleteMine(Long notificationId) {
        int deleted = notificationRepo.deleteByIdAndUserId(notificationId, getCurrentUserId());
        return deleted > 0;
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setTitle(n.getTitle());
        r.setContent(n.getBody());
        r.setRead(n.isRead());
        r.setCreatedAt(n.getCreatedAt());
        return r;
    }
}

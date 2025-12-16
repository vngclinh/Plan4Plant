package com.example.plant_sever.service;

import com.example.plant_sever.DAO.DeviceTokenRepo;
import com.example.plant_sever.DAO.NotificationRepo;
import com.example.plant_sever.model.DeviceToken;
import com.example.plant_sever.model.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DeviceTokenRepo deviceTokenRepo;
    private final NotificationRepo notificationRepo;
    private final HttpClient httpClient;

    // Legacy FCM server key — you should prefer using OAuth / Firebase Admin for production.
    @Value("${fcm.server.key:}")
    private String fcmServerKey;

    public NotificationService(DeviceTokenRepo deviceTokenRepo, NotificationRepo notificationRepo) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.notificationRepo = notificationRepo;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

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

    public void sendToToken(String token, String title, String body, Map<String, String> data) {
        if (fcmServerKey == null || fcmServerKey.isBlank()) {
            log.warn("FCM server key is not configured; skipping push send");
            return;
        }
        String payload = buildSinglePayload(token, title, body, data);
        sendPayload(payload, List.of(token));
    }

    public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data, Long userId) {
        if (fcmServerKey == null || fcmServerKey.isBlank()) {
            log.warn("FCM server key is not configured; skipping push send");
            return;
        }
        if (tokens == null || tokens.isEmpty()) return;
        String payload = buildMulticastPayload(tokens, title, body, data);
        sendPayload(payload, tokens);
        //lưu thông báo vào db 
        saveNotification(userId, title, body);
    }

    // Build JSON payload for a single token (legacy FCM endpoint)
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

            // Simple handling: if response mentions invalid/not-registered tokens, remove them
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

    public void saveNotification(Long userId, String title, String body) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        notificationRepo.save(notification);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        notificationRepo.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepo.save(n);
        });
        return ResponseEntity.ok().build();
    }
}

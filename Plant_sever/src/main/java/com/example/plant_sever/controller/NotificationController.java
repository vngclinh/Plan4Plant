package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.NotificationCreateRequest;
import com.example.plant_sever.DTO.NotificationResponse;
import com.example.plant_sever.DTO.RegisterTokenRequest;
import com.example.plant_sever.DTO.SendNotificationRequest;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.NotificationService;
import com.example.plant_sever.DAO.UserRepo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepo userRepo;

    public NotificationController(NotificationService notificationService, UserRepo userRepo) {
        this.notificationService = notificationService;
        this.userRepo = userRepo;
    }

    // ================================================================
    // 🔒 getCurrentUser giống GardenService
    // ================================================================
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

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
    // ✅ Register token cho CHÍNH MÌNH (không cần userId)
    // ================================================================
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterTokenRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()) {
            return ResponseEntity.badRequest().body("token is required");
        }
        notificationService.saveOrUpdateToken(getCurrentUserId(), req.getToken(), req.getPlatform());
        return ResponseEntity.ok().build();
    }

    // ================================================================
    // ✅ GET notifications của mình
    // ================================================================
    @GetMapping("/me")
    public List<NotificationResponse> getMine() {
        return notificationService.getByUser(getCurrentUserId());
    }

    @GetMapping("/me/unread/count")
    public Map<String, Long> countUnreadMine() {
        return Map.of("count", notificationService.getUnreadCount(getCurrentUserId()));
    }

    // ================================================================
    // ✅ Mark read/unread của mình
    // ================================================================
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        boolean updated = notificationService.updateReadFlagMine(id, true);
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/unread")
    public ResponseEntity<?> markAsUnread(@PathVariable Long id) {
        boolean updated = notificationService.updateReadFlagMine(id, false);
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/me/read-all")
    public ResponseEntity<?> markAllAsReadMine() {
        int updated = notificationService.markAllAsReadMine();
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMine(@PathVariable Long id) {
        boolean deleted = notificationService.deleteMine(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ================================================================
    // (Optional) tạo local noti cho CHÍNH MÌNH
    // ================================================================
    @PostMapping("/local/me")
    public ResponseEntity<?> saveLocalMine(@RequestBody NotificationCreateRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("title is required");
        }
        if (req.getBody() == null || req.getBody().isBlank()) {
            return ResponseEntity.badRequest().body("body is required");
        }

        notificationService.saveNotification(getCurrentUserId(), req.getTitle(), req.getBody());
        return ResponseEntity.ok().build();
    }

    // ================================================================
    // (Optional) ADMIN/DEBUG APIs (giữ lại nếu bạn cần)
    // ================================================================
    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody SendNotificationRequest req) {
        if (req.getTargetTokens() == null || req.getTargetTokens().isEmpty()) {
            return ResponseEntity.badRequest().body("targetTokens required");
        }
        notificationService.sendToTokens(req.getTargetTokens(), req.getTitle(), req.getBody(), req.getData(), req.getUserId());
        return ResponseEntity.ok().body("sent");
    }

    @PostMapping("/send/user/{userId}")
    public ResponseEntity<?> sendToUser(@PathVariable Long userId, @RequestBody SendNotificationRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("title is required");
        }
        if (req.getBody() == null || req.getBody().isBlank()) {
            return ResponseEntity.badRequest().body("body is required");
        }
        notificationService.sendToUser(userId, req.getTitle(), req.getBody(), req.getData());
        return ResponseEntity.ok("sent");
    }
}

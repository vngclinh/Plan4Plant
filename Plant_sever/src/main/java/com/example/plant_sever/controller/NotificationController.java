package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.NotificationResponse;
import com.example.plant_sever.DAO.NotificationRepo;
import com.example.plant_sever.model.Notification;
import com.example.plant_sever.DTO.RegisterTokenRequest;
import com.example.plant_sever.DTO.SendNotificationRequest;
import com.example.plant_sever.service.NotificationService;

import java.util.Map;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepo notificationRepo;

    public NotificationController(NotificationService notificationService,
                                  NotificationRepo notificationRepo) {
        this.notificationService = notificationService;
        this.notificationRepo = notificationRepo;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterTokenRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()) {
            return ResponseEntity.badRequest().body("token is required");
        }
        notificationService.saveOrUpdateToken(req.getUserId(), req.getToken(), req.getPlatform());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody SendNotificationRequest req) {
        if (req.getTargetTokens() == null || req.getTargetTokens().isEmpty()) {
            return ResponseEntity.badRequest().body("targetTokens required");
        }
        notificationService.sendToTokens(req.getTargetTokens(), req.getTitle(), req.getBody(), req.getData(), req.getUserId());
        return ResponseEntity.ok().body("sent");
    }

    @PostMapping("/local")
    public ResponseEntity<?> saveLocal(@RequestBody Map<String, String> req) {
        Long userId = Long.valueOf(req.get("userId"));
        String title = req.get("title");
        String body = req.get("body");

        notificationService.saveNotification(userId, title, body);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> getByUser(@PathVariable Long userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(n -> {
                    NotificationResponse r = new NotificationResponse();
                    r.setId(n.getId());
                    r.setTitle(n.getTitle());
                    r.setContent(n.getBody()); 
                    r.setRead(n.isRead());
                    r.setCreatedAt(n.getCreatedAt());
                    return r;
                })
                .toList();
    }
}


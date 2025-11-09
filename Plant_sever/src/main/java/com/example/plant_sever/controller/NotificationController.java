package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.RegisterTokenRequest;
import com.example.plant_sever.DTO.SendNotificationRequest;
import com.example.plant_sever.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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
        notificationService.sendToTokens(req.getTargetTokens(), req.getTitle(), req.getBody(), req.getData());
        return ResponseEntity.ok().body("sent");
    }
}


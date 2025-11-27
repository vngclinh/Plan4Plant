package com.example.plant_sever.controller;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ChatHistoryResponse;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.ChatHistoryService;
import com.example.plant_sever.service.GeminiService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final GeminiService geminiService;
    private final UserRepo userRepo;
    private final ChatHistoryService chatHistoryService;

    public ChatController(GeminiService geminiService, UserRepo userRepo, ChatHistoryService chatHistoryService) {
        this.geminiService = geminiService;
        this.userRepo = userRepo;
        this.chatHistoryService = chatHistoryService;
    }

    // 🗨️ Trường hợp chỉ gửi TEXT (application/json)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String chatText(@RequestBody Map<String, String> body) {
        String message = body.get("message");

        if (message == null || message.isEmpty())
            return "⚠️ Bạn chưa gửi nội dung nào (text).";

        Long userId = getCurrentUserId();
        return geminiService.askGemini(message, userId);
    }

    // 📸 Trường hợp gửi ảnh (multipart/form-data)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String chatImage(@RequestPart("message") String message,
                            @RequestPart("image") MultipartFile imageFile) {
        Long userId = getCurrentUserId();
        return geminiService.askGeminiWithImage(message, imageFile, userId);
    }

    @GetMapping("/today")
    public java.util.List<ChatHistoryResponse> getTodayChat() {
        Long userId = getCurrentUserId();
        return chatHistoryService.getTodayChatResponses(userId);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));
        return user.getId();
    }
}

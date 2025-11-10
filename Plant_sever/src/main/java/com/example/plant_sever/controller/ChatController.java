package com.example.plant_sever.controller;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.GeminiService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final GeminiService geminiService;
    private final UserRepo userRepo;

    public ChatController(GeminiService geminiService, UserRepo userRepo) {
        this.geminiService = geminiService;
        this.userRepo = userRepo;
    }

    // ✅ Một endpoint duy nhất hỗ trợ cả JSON (text) và multipart (ảnh + text)
    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public String chat(
            @RequestPart(value = "message", required = false) String message,
            @RequestPart(value = "image", required = false) MultipartFile imageFile
    ) {
        // 🔐 Lấy thông tin người dùng từ JWT
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));

        Long userId = user.getId();

        // 📸 Nếu có ảnh thì gọi Gemini xử lý ảnh + text
        if (imageFile != null && !imageFile.isEmpty()) {
            return geminiService.askGeminiWithImage(message, imageFile, userId);
        }

        // 💬 Nếu chỉ có text
        if (message != null && !message.isEmpty()) {
            return geminiService.askGemini(message, userId);
        }

        return "⚠️ Bạn chưa gửi nội dung nào (text hoặc ảnh).";
    }
}

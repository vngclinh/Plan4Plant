package com.example.plant_sever.service;

import com.example.plant_sever.model.ChatHistory;
import com.example.plant_sever.model.User;

import lombok.RequiredArgsConstructor;

import com.example.plant_sever.DAO.ChatHistoryRepo;
import com.example.plant_sever.DAO.UserRepo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepo chatHistoryRepository;
    private final UserRepo userRepo;

    public void saveChatTurn(Long userId, String message, String response) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatHistory chat = ChatHistory.builder()
                .user(user)
                .role("user")
                .message(message)
                .response(response)
                .createdAt(LocalDateTime.now())
                .build();

        chatHistoryRepository.save(chat);
    }

    // 🟢 Lấy các lượt chat gần nhất (3 ngày qua)
    public List<ChatHistory> getRecentChats(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        return chatHistoryRepository.findRecentChats(userId, since);
    }

    @Scheduled(cron = "0 0 3 * * *") // 3h sáng hàng ngày
    public void cleanupOldChats() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        chatHistoryRepository.deleteByCreatedAtBefore(cutoff);
        System.out.println("🧹 Đã xoá chat cũ hơn 3 ngày");
    }
}

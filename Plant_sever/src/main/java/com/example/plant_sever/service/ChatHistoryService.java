package com.example.plant_sever.service;

import com.example.plant_sever.DAO.ChatHistoryRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ChatHistoryResponse;
import com.example.plant_sever.model.ChatHistory;
import com.example.plant_sever.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    // Get chats within last 24h (used for Gemini context)
    public List<ChatHistory> getRecentChats(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        return chatHistoryRepository.findRecentChats(userId, since);
    }

    // Return today's chat history for UI preload
    public List<ChatHistoryResponse> getTodayChatResponses(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return chatHistoryRepository.findRecentChats(userId, start).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Enforce daily question quota by user level
    public void validateQuota(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int limit = switch (user.getLevel()) {
            case MAM -> 10;
            case TRUONG_THANH -> 20;
            case CO_THU -> 30;
        };

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long todayCount = chatHistoryRepository.countByUser_IdAndCreatedAtBetween(userId, start, end);

        if (todayCount >= limit) {
            throw new RuntimeException(String.format(
                    "Ban da het %d luot hoi hom nay cho cap do %s. Thu lai vao ngay mai nhe.",
                    limit,
                    user.getLevel()));
        }
    }

    @Scheduled(cron = "0 0 3 * * *") // 3h sang hang ngay
    @Transactional
    public void cleanupOldChats() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        chatHistoryRepository.deleteByCreatedAtBefore(cutoff);
        System.out.println("Da xoa chat cu hon 3 ngay");
    }

    private ChatHistoryResponse toResponse(ChatHistory chat) {
        return ChatHistoryResponse.builder()
                .id(chat.getId())
                .role(chat.getRole())
                .message(chat.getMessage())
                .response(chat.getResponse())
                .createdAt(chat.getCreatedAt())
                .build();
    }
}

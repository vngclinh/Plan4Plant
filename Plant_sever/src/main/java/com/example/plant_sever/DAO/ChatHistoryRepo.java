package com.example.plant_sever.DAO;

import com.example.plant_sever.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatHistoryRepo extends JpaRepository<ChatHistory, Long> {

    @Query("SELECT c FROM ChatHistory c " +
           "WHERE c.user.id = :userId AND c.createdAt >= :since " +
           "ORDER BY c.createdAt ASC")
    List<ChatHistory> findRecentChats(Long userId, LocalDateTime since);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String role; // "user" hoặc "bot"

    @Column(columnDefinition = "TEXT")
    private String message;  // câu hỏi người dùng

    @Column(columnDefinition = "TEXT")
    private String response; // phản hồi từ bot

    private LocalDateTime createdAt = LocalDateTime.now();
}

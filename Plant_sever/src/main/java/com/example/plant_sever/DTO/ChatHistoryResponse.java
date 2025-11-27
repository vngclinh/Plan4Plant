package com.example.plant_sever.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    private Long id;
    private String role;
    private String message;
    private String response;
    private LocalDateTime createdAt;
}

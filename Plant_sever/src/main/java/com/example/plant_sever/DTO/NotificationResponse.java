package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationResponse {

    private Long id;
    private String title;
    private String content;
    private boolean isRead;
    private LocalDateTime createdAt;
}

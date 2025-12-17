package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNotificationRequest {

    private Long userId;
    private String title;
    private String content;
}

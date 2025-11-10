package com.example.plant_sever.DTO;

import java.time.LocalDate;
import lombok.Data;

@Data
public class DiaryResponse {

    private Long id;
    private Long gardenId; // ID của cây mà nhật ký này thuộc về
    private LocalDate entryTime;
    private String content;

}
package com.example.plant_sever.DTO;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AddDiaryRequest {

    private String content;

    private LocalDate entryTime;
}
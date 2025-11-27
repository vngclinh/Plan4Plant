package com.example.plant_sever.DTO;

import com.example.plant_sever.model.DiseaseStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GardenDiseaseResponse {
    private Long gardenDiseaseId;
    private Long diseaseId;
    private String name;
    private String scientificName;
    private LocalDateTime detectedDate;
    private LocalDateTime curedDate;
    private DiseaseStatus status;
}


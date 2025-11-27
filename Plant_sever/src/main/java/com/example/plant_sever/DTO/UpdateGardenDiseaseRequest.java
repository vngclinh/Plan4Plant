package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGardenDiseaseRequest {
    private Long gardenDiseaseId;
    private String status;
    private String note;
    private String detectedDate;
    private String curedDate;
}

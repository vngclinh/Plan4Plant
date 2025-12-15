package com.example.plant_sever.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentPlanResult {
    private boolean success;
    private String status;
    private String message;
    private Long gardenId;
    private String gardenNickname;
    private String plantName;
    private String diseaseName;
    private List<TreatmentPlanAction> currentSchedule;
    private List<TreatmentPlanAction> proposedActions;
}

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
public class TreatmentPlanAction {
    private String type;
    private String scheduledTime;
    private String description;
    private List<String> impactedEvents;
}
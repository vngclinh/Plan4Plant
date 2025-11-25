package com.example.plant_sever.DTO;

import com.example.plant_sever.model.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class GardenResponse {
    private Long id;
    private String nickname;
    private Plant plant;
    private LocalDateTime dateAdded;
    private GardenStatus status;
    private GardenType type;
    private PotType potType;
    private List<GardenDiseaseResponse> diseases;
    private Map<Long, DiseaseStatus> diseaseStatuses;
    private Map<Long, LocalDateTime> detectedDates;
    private List<DiaryResponse> diaries;
}
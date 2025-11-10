package com.example.plant_sever.DTO;

import com.example.plant_sever.model.GardenStatus;

import com.example.plant_sever.model.GardenType;
import com.example.plant_sever.model.PotType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddGardenRequest {
    private Long plantId;
    private GardenStatus status;
    private String nickname;
    private String diary;
    private GardenType type;
    private PotType potType;
    private List<Long> diseaseIds;
}

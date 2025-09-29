package com.example.plant_sever.DTO;

import com.example.plant_sever.model.GardenStatus;

import com.example.plant_sever.model.GardenType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddGardenRequest {
    private Long plantId;
    private GardenStatus status;
    private GardenType type;
}

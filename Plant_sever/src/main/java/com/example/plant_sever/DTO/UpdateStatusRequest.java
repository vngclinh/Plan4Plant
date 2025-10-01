package com.example.plant_sever.DTO;

import com.example.plant_sever.model.GardenStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStatusRequest {
    private GardenStatus status;
}

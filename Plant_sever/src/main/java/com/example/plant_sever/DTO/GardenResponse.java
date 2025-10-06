package com.example.plant_sever.DTO;

import com.example.plant_sever.model.GardenStatus;
import com.example.plant_sever.model.GardenType;
import com.example.plant_sever.model.PotType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GardenResponse {
    private Long id;
    private String nickname;
    private String plantName;
    private GardenStatus status;
    private GardenType type;
    private PotType potType;// just the name
    private List<String> diseaseNames;  // just names
}
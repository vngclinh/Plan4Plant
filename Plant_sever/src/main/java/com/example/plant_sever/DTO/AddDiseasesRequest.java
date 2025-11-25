package com.example.plant_sever.DTO;

import lombok.Data;

import java.util.List;

@Data
public class AddDiseasesRequest {
    private Long gardenId;
    private List<Long> diseaseIds;
}

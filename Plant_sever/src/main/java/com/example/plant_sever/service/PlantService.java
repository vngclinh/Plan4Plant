package com.example.plant_sever.service;

import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.model.Plant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantService {
    private PlantRepo plantRepo;
    public List<Plant> searchPlants(String keyword) {
        return plantRepo.searchByName(keyword.trim().toLowerCase());
    }
}

package com.example.plant_sever.service;

import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.model.Plant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlantService {

    private final PlantRepo plantRepo; // <-- must be final

    public List<Plant> searchPlants(String keyword) {
        return plantRepo.searchPlants(keyword.trim().toLowerCase());
    }

    public List<Plant> getAll() {
        return plantRepo.findAll();
    }

    public Optional<Plant> getById(Long id) {
        return plantRepo.findById(id);
    }
}
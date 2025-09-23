package com.example.plant_sever.service;

import java.time.LocalDateTime;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.AddGardenRequest;
import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.GardenStatus;
import com.example.plant_sever.model.Plant;
import com.example.plant_sever.model.User;
import org.springframework.http.HttpStatus;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GardenService {

    private final UserRepo userRepo;
    private final PlantRepo plantRepo;
    private final GardenRepo gardenRepo;

    public Garden addPlantToGarden(AddGardenRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Plant plant = plantRepo.findById(request.getPlantId())
                .orElseThrow(() -> new RuntimeException("Plant not found"));

        boolean exists = gardenRepo.existsByUserAndPlant(user, plant);
        if (exists) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Plant already exists in garden"
            );
        }
        Garden garden = new Garden();
        garden.setUser(user);
        garden.setPlant(plant);
        garden.setStatus(GardenStatus.ALIVE); // set mặc định
        garden.setDateAdded(LocalDateTime.now());

        return gardenRepo.save(garden);
    }

}

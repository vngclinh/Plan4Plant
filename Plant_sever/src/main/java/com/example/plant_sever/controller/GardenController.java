package com.example.plant_sever.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.AddGardenRequest;
import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.GardenService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;



@RestController
@RequestMapping("/garden")
@RequiredArgsConstructor
public class GardenController {
    private final GardenService gardenService;
    private final UserRepo userRepo;
    private final GardenRepo gardenRepo;
    private final PlantRepo plantRepo;
    @PostMapping("/add")
    public ResponseEntity<Garden> addPlantToGarden(@RequestBody AddGardenRequest request) {
        Garden garden = gardenService.addPlantToGarden(request);
        return ResponseEntity.ok(garden);
    }
    
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkExists(
            @RequestParam Long plantId,
            Authentication authentication) {
        String username = authentication.getName();
        User user = userRepo.findByUsername(username)
                .orElseThrow();
        boolean exists = gardenRepo.existsByUserAndPlant(user,
                plantRepo.findById(plantId).orElseThrow());
        return ResponseEntity.ok(exists);
    }

}

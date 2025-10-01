package com.example.plant_sever.service;

import java.time.LocalDateTime;
import java.util.List;

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

import jakarta.transaction.Transactional;

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

        Garden garden = new Garden();
        garden.setUser(user);
        garden.setPlant(plant);
        garden.setStatus(GardenStatus.ALIVE); // set mặc định
        garden.setDateAdded(LocalDateTime.now());

        return gardenRepo.save(garden);
    }

    public List<Garden> getUserGarden() {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return gardenRepo.findByUserId(user.getId());
    }

    @Transactional
    public Garden updateNickname(Long gardenId, String nickname){
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId).orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())){
            System.out.println("Garden userId = " + garden.getUser().getId());
            System.out.println("Current userId = " + user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your garden");
        }
        garden.setNickname(nickname);
        return gardenRepo.save(garden);
    }

    @Transactional
    public Garden updateStatus(Long gardenId, GardenStatus status){
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        Garden garden = gardenRepo.findById(gardenId).orElseThrow(() -> new RuntimeException("Plant not found"));
        if(!garden.getUser().getId().equals(user.getId())){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your garden");
        }
        garden.setStatus(status);
        return gardenRepo.save(garden);
    }
    @Transactional
    public void removePlantFromGarden(Long gardenId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your garden");
        }

        gardenRepo.delete(garden);
    }
}

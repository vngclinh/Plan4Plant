package com.example.plant_sever.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.plant_sever.DAO.DiseaseRepo;
import com.example.plant_sever.DTO.GardenResponse;
import com.example.plant_sever.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.AddGardenRequest;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GardenService {

    private final UserRepo userRepo;
    private final PlantRepo plantRepo;
    private final GardenRepo gardenRepo;
    private final DiseaseRepo diseaseRepo;

    @Transactional
    public GardenResponse addPlantToGarden(AddGardenRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Plant plant = plantRepo.findById(request.getPlantId())
                .orElseThrow(() -> new RuntimeException("Plant not found"));

        // Generate unique nickname

        Garden garden = new Garden();
        garden.setUser(user);
        garden.setPlant(plant);
        String uniqueNickname = generateUniqueNickname(user, garden.getPlant().getCommonName());
        garden.setNickname(uniqueNickname);
        garden.setDateAdded(LocalDateTime.now());
        garden.setType(request.getType());
        garden.setPotType(request.getPotType());
        garden.setStatus(request.getStatus() != null ? request.getStatus() : GardenStatus.ALIVE);
        garden.setDateAdded(LocalDateTime.now());

        // Handle optional disease IDs
        garden.setDiseases(new ArrayList<>());
        if (request.getDiseaseIds() != null && !request.getDiseaseIds().isEmpty()) {
            List<Disease> diseases = diseaseRepo.findAllById(request.getDiseaseIds());
            garden.getDiseases().addAll(diseases);
        }

        return toResponse(gardenRepo.save(garden));
    }

    public List<GardenResponse> getUserGarden() {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Garden> gardens = gardenRepo.findByUserId(user.getId());
        return gardens.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public GardenResponse updateGarden(Long gardenId, AddGardenRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }

        // Update nickname with uniqueness check
        if (request.getNickname() != null) {
            String newNickname = request.getNickname().trim();
            if (!newNickname.equals(garden.getNickname())) {
                newNickname = generateUniqueNickname(user, newNickname);
                garden.setNickname(newNickname);
            }
        }

        if (request.getStatus() != null) garden.setStatus(request.getStatus());
        if (request.getType() != null) garden.setType(request.getType());
        if (request.getPotType() != null) garden.setPotType(request.getPotType());

        // Update diseases
        if (request.getDiseaseIds() != null) {
            List<Disease> diseases = request.getDiseaseIds().isEmpty() ?
                    new ArrayList<>() :
                    diseaseRepo.findAllById(request.getDiseaseIds());
            garden.setDiseases(diseases);
        }

        // Update plant if provided
        if (request.getPlantId() != null) {
            Plant plant = plantRepo.findById(request.getPlantId())
                    .orElseThrow(() -> new RuntimeException("Plant not found"));
            garden.setPlant(plant);
        }

        return toResponse(gardenRepo.save(garden));
    }

    @Transactional
    public void removePlantFromGarden(Long gardenId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }

        gardenRepo.delete(garden);
    }


    private String generateUniqueNickname(User user, String baseNickname) {
        if (baseNickname == null || baseNickname.isBlank()) {
            baseNickname = "My Plant";
        }

        String nickname = baseNickname.trim();
        int counter = 1;

        while (gardenRepo.existsByUserAndNickname(user, nickname)) {
            nickname = baseNickname + " (" + counter + ")";
            counter++;
        }

        return nickname;
    }

    private GardenResponse toResponse(Garden garden) {
        GardenResponse response = new GardenResponse();
        response.setId(garden.getId());
        response.setNickname(garden.getNickname());
        response.setPlant(garden.getPlant());
        response.setStatus(garden.getStatus());
        response.setDateAdded(garden.getDateAdded());
        response.setType(garden.getType());
        response.setPotType(garden.getPotType());
        response.setDiseaseNames(garden.getDiseases().stream()
                .map(Disease::getName)
                .collect(Collectors.toList()));
        return response;
    }
}

package com.example.plant_sever.controller;

import com.example.plant_sever.model.Plant;

import com.example.plant_sever.service.PlantService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plants")
@RequiredArgsConstructor
public class PlantController {
    private final PlantService plantService;


    @GetMapping("/search")
    public ResponseEntity<List<Plant>> searchPlants(@RequestParam("keyword") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<Plant> results = plantService.searchPlants(keyword);
        return ResponseEntity.ok(results);
    }

    // Get all plants
    @GetMapping
    public ResponseEntity<List<Plant>> getAllPlants() {
        return ResponseEntity.ok(plantService.getAll());
    }

    // Get plant by id
    @GetMapping("/{id}")
    public ResponseEntity<Plant> getPlantById(@PathVariable Long id) {
        return plantService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.example.plant_sever.controller;

import com.example.plant_sever.model.Plant;

import com.example.plant_sever.service.PlantService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/plants")
public class PlantController {
    private final PlantService plantService;
    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }
    @GetMapping("/search")
    public List<Plant> search(@RequestParam String keyword) {
        return plantService.searchPlants(keyword);
    }
}

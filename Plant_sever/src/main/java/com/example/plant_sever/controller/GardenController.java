package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.GardenResponse;
import com.example.plant_sever.DTO.GardenUpdateRequest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.AddGardenRequest;
import com.example.plant_sever.service.GardenService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;




@RestController
@RequestMapping("/garden")
@RequiredArgsConstructor
public class GardenController {
    private final GardenService gardenService;

    @PostMapping("/add")
    public ResponseEntity<GardenResponse> addPlantToGarden(@RequestBody AddGardenRequest request) {
        return ResponseEntity.ok(gardenService.addPlantToGarden(request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<GardenResponse>> getMyGarden() {
        return ResponseEntity.ok(gardenService.getUserGarden());
    }

    @PutMapping("/{gardenId}/")
    public ResponseEntity<GardenResponse> updateGarden(@PathVariable Long gardenId,
                                                       @RequestBody GardenUpdateRequest request) {
        return ResponseEntity.ok(gardenService.updateGarden(gardenId, request));
    }

    @DeleteMapping("/{gardenId}")
    public ResponseEntity<Void> removePlant(@PathVariable Long gardenId) {
        gardenService.removePlantFromGarden(gardenId);
        return ResponseEntity.noContent().build();
    }
}

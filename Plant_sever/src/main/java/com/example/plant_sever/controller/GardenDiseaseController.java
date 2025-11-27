package com.example.plant_sever.controller;


import com.example.plant_sever.DTO.AddDiseasesRequest;

import com.example.plant_sever.DTO.UpdateGardenDiseaseRequest;

import com.example.plant_sever.service.GardenService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/garden-disease")
@RequiredArgsConstructor
public class GardenDiseaseController {

    private final GardenService gardenService;

    @PostMapping("/add")
    public ResponseEntity<?> addDiseases(@RequestBody AddDiseasesRequest req) {
        try {
            return ResponseEntity.ok(
                    gardenService.addDiseasesToGarden(req.getGardenId(), req.getDiseaseIds())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateDisease(@RequestBody UpdateGardenDiseaseRequest req) {
        try {
            return ResponseEntity.ok(gardenService.updateGardenDisease(req));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGardenDisease(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(gardenService.deleteGardenDisease(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/garden/{gardenId}")
    public ResponseEntity<?> getDiseasesOfGarden(@PathVariable Long gardenId) {
        try {
            return ResponseEntity.ok(gardenService.getDiseasesOfGarden(gardenId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getGardenDiseaseById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(gardenService.getGardenDiseaseById(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

}

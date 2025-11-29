package com.example.plant_sever.controller;

import com.example.plant_sever.model.Disease;
import com.example.plant_sever.service.DiseaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diseases")
@RequiredArgsConstructor
public class DiseaseController {

    private final DiseaseService diseaseService;

    // Get all diseases
    @GetMapping
    public ResponseEntity<List<Disease>> getAllDiseases() {
        return ResponseEntity.ok(diseaseService.getAllDiseases());
    }

    // Get disease by ID
    @GetMapping("/{id}")
    public ResponseEntity<Disease> getDiseaseById(@PathVariable Long id) {
        return ResponseEntity.ok(diseaseService.getDiseaseById(id));
    }

    // Search diseases by name (supports Vietnamese)
    @GetMapping("/search")
    public ResponseEntity<List<Disease>> searchDiseases(@RequestParam String keyword) {
        return ResponseEntity.ok(diseaseService.searchDiseases(keyword));
    }

    @GetMapping("/search/fuzzy")
    public ResponseEntity<List<Disease>> fuzzySearch(@RequestParam String keyword) {
        return ResponseEntity.ok(diseaseService.fuzzySearch(keyword));
    }
}


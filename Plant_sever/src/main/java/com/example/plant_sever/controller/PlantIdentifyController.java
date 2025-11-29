package com.example.plant_sever.controller;

import com.example.plant_sever.model.PlantResponse;
import com.example.plant_sever.service.PlantNetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/plant")
@RequiredArgsConstructor
public class PlantIdentifyController {

    private final PlantNetService plantNetService;

    @PostMapping("/identify")
    public ResponseEntity<PlantResponse> identifyPlant(
            @RequestPart("image") MultipartFile image,
            @RequestPart("organ") String organ
    ) {
        try {
            PlantResponse result = plantNetService.identify(image, organ);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}

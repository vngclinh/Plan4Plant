package com.example.plant_sever.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.plant_sever.model.GardenImage;
import com.example.plant_sever.service.GardenImageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/garden")
@RequiredArgsConstructor
public class GardenImageController {
    private final GardenImageService gardenImageService;

    @PostMapping("/{gardenId}/images")
    public ResponseEntity<GardenImage> uploadImage(
            @PathVariable Long gardenId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(gardenImageService.uploadImage(gardenId, file));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        gardenImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}
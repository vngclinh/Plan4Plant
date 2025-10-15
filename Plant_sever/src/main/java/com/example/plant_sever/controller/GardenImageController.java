package com.example.plant_sever.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.plant_sever.DAO.GardenImageRepo;
import com.example.plant_sever.model.GardenImage;
import com.example.plant_sever.service.GardenImageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/garden")
@RequiredArgsConstructor
public class GardenImageController {
    private final GardenImageService gardenImageService;
    private final GardenImageRepo gardenImageRepo;

    @GetMapping("/{id}/images")
    public ResponseEntity<List<GardenImage>> getGardenImages(@PathVariable Long id) {
        List<GardenImage> images = gardenImageRepo.findByGardenId(id);
        return ResponseEntity.ok(images);
    }
    
    @PostMapping("/{gardenId}/images")
    public ResponseEntity<GardenImage> uploadImage(
            @PathVariable Long gardenId,
            @RequestParam("file") MultipartFile file
    ) {
        GardenImage image = gardenImageService.uploadImage(gardenId, file);
        return ResponseEntity.ok(image);
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<String> deleteImage(@PathVariable Long imageId) {
        gardenImageService.deleteImage(imageId);
        return ResponseEntity.ok("Image deleted successfully");
    }
}
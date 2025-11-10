package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.AddDiaryRequest;
import com.example.plant_sever.DTO.DiaryResponse;
import com.example.plant_sever.DTO.GardenResponse;
import com.example.plant_sever.service.GardenService; 

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/garden/{gardenId}/diaries") // Endpoint liên quan đến Garden ID
@RequiredArgsConstructor
public class DiaryController {

    private final GardenService gardenService;

    @PostMapping
    public ResponseEntity<GardenResponse> addDiaryEntry(
            @PathVariable Long gardenId,
            @RequestBody AddDiaryRequest request) {
        
        GardenResponse response = gardenService.addDiaryEntry(gardenId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED); 
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> removeDiaryEntry(
            @PathVariable Long gardenId, 
            @PathVariable Long diaryId) {
        
        gardenService.removeDiaryEntry(diaryId);
        return ResponseEntity.noContent().build(); 
    }

    @GetMapping 
    public ResponseEntity<List<DiaryResponse>> getDiariesByGardenId(@PathVariable Long gardenId) {
            List<DiaryResponse> diaries = gardenService.getDiariesByGardenId(gardenId);
            return ResponseEntity.ok(diaries); // Trả về 200 OK
    }
}
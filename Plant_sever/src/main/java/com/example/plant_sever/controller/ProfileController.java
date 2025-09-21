package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.ProfileRequest;
import com.example.plant_sever.model.Profile;
import com.example.plant_sever.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<List<Profile>> getMyProfiles(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return ResponseEntity.ok(profileService.showAllProfiles(token));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Profile>> searchProfiles(@RequestParam String keyword) {
        return ResponseEntity.ok(profileService.findByKeyword(keyword));
    }


    @GetMapping("/{id}")
    public ResponseEntity<Profile> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.findbyid(id));
    }


    @PostMapping
    public ResponseEntity<Profile> createProfile(
            @RequestBody ProfileRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return ResponseEntity.ok(profileService.addProfile(request, token));
    }


    @PutMapping("/{id}")
    public ResponseEntity<Profile> updateProfile(
            @PathVariable Long id,
            @RequestBody ProfileRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return ResponseEntity.ok(profileService.updateProfile(id, request, token));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        profileService.deleteProfile(id, token);
        return ResponseEntity.noContent().build();
    }
}
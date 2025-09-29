package com.example.plant_sever.controller;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.GardenScheduleRequest;
import com.example.plant_sever.DTO.GardenScheduleResponse;
import com.example.plant_sever.model.Completion;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.GardenScheduleService;
import com.example.plant_sever.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class GardenScheduleController {

    private final GardenScheduleService scheduleService;
    private final UserRepo userRepository;

    @PostMapping
    public ResponseEntity<GardenScheduleResponse> create(@RequestBody GardenScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GardenScheduleResponse> update(@PathVariable Long id,
                                                         @RequestBody GardenScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GardenScheduleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getById(id));
    }

    @GetMapping("/garden/{gardenId}")
    public ResponseEntity<List<GardenScheduleResponse>> getByGarden(@PathVariable Long gardenId) {
        return ResponseEntity.ok(scheduleService.getByGarden(gardenId));
    }

    @GetMapping("/completion/{status}")
    public ResponseEntity<List<GardenScheduleResponse>> getByCompletion(@PathVariable Completion status) {
        return ResponseEntity.ok(scheduleService.getByCompletion(status));
    }

    @GetMapping
    public ResponseEntity<List<GardenScheduleResponse>> getAll() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return ResponseEntity.ok(scheduleService.getAll(user));
    }
}
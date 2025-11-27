package com.example.plant_sever.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.plant_sever.DAO.*;
import com.example.plant_sever.DTO.*;
import com.example.plant_sever.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GardenService {

    private final UserRepo userRepo;
    private final PlantRepo plantRepo;
    private final GardenRepo gardenRepo;
    private final DiseaseRepo diseaseRepo;
    private final DiaryRepo diaryRepo;
    private final GardenDiseaseRepo gardenDiseaseRepository;

    // ================================================================
    // 🔒 UTILITY
    // ================================================================
    private User getCurrentUser() {
        String username = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        return userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void validateOwner(Garden garden, User user) {
        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }
    }

    private void validateOwner(GardenDisease gd, User user) {
        validateOwner(gd.getGarden(), user);
    }

    // ================================================================
    // 🌱 CREATE GARDEN
    // ================================================================
    @Transactional
    public GardenResponse addPlantToGarden(AddGardenRequest request) {

        User user = getCurrentUser();
        Plant plant = plantRepo.findById(request.getPlantId())
                .orElseThrow(() -> new RuntimeException("Plant not found"));

        Garden garden = new Garden();
        garden.setUser(user);
        garden.setPlant(plant);
        garden.setNickname(generateNickname(user, request.getNickname(), plant.getCommonName()));
        garden.setType(Optional.ofNullable(request.getType()).orElse(GardenType.Indoor));
        garden.setPotType(Optional.ofNullable(request.getPotType()).orElse(PotType.MEDIUM));
        garden.setStatus(Optional.ofNullable(request.getStatus()).orElse(GardenStatus.ALIVE));
        garden.setDateAdded(LocalDateTime.now());
        garden.setGardenDiseases(new ArrayList<>());
        garden.setDiaries(new ArrayList<>());

        addDiseases(garden, request.getDiseaseIds());
        gardenRepo.save(garden);

        return toResponse(garden);
    }

    public void addDiseases(Garden garden, List<Long> diseaseIds) {
        if (diseaseIds == null || diseaseIds.isEmpty()) return;

        List<Disease> diseases = diseaseRepo.findAllById(diseaseIds);

        for (Disease d : diseases) {

            // 1. Nếu bệnh đang ACTIVE → chỉ cập nhật detectDate
            GardenDisease existingActive = garden.getGardenDiseases().stream()
                    .filter(x -> Objects.equals(x.getDisease().getId(), d.getId()))
                    .filter(x -> x.getStatus() == DiseaseStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);

            if (existingActive != null) {
                existingActive.setDetectedDate(LocalDateTime.now());
                gardenDiseaseRepository.save(existingActive);  // SAVE!!!
                continue;
            }

            // 2. Tạo record mới
            GardenDisease newGd = new GardenDisease();
            newGd.setGarden(garden);
            newGd.setDisease(d);
            newGd.setStatus(DiseaseStatus.ACTIVE);
            newGd.setDetectedDate(LocalDateTime.now());

            // Lưu vào DB trước
            gardenDiseaseRepository.save(newGd);

            // Cập nhật vào list trong garden (để trả response)
            garden.getGardenDiseases().add(newGd);
        }
    }

    @Transactional
    public List<GardenDiseaseResponse> addDiseasesToGarden(Long gardenId, List<Long> diseaseIds) {

        User user = getCurrentUser();

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        validateOwner(garden, user);

        // Add or update diseases
        addDiseases(garden, diseaseIds);

        // Save garden (optional but safe)
        gardenRepo.save(garden);

        // 🔥 Return list of mapped GardenDiseaseResponse
        return garden.getGardenDiseases().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public GardenDiseaseResponse updateGardenDisease(UpdateGardenDiseaseRequest req) {

        GardenDisease gd = gardenDiseaseRepository.findById(req.getGardenDiseaseId())
                .orElseThrow(() -> new RuntimeException("Disease record not found"));

        validateOwner(gd, getCurrentUser());

        if (req.getStatus() != null) {
            try {
                gd.setStatus(DiseaseStatus.valueOf(req.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }

        if (req.getDetectedDate() != null) {
            gd.setDetectedDate(parseDateTime(req.getDetectedDate(), "Invalid detectedDate format"));
        }

        if (req.getCuredDate() != null) {
            gd.setCuredDate(parseDateTime(req.getCuredDate(), "Invalid curedDate format"));
        }

        gardenDiseaseRepository.save(gd);
        return mapToResponse(gd);
    }

    // ================================================================
    // 🌿 READ GARDENS
    // ================================================================
    public List<GardenResponse> getUserGarden() {
        User user = getCurrentUser();

        return gardenRepo.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public GardenResponse getGardenById(Long gardenId) {
        GardenResponse response = gardenRepo.findById(gardenId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Garden not found"));
        return response;
    }

    public List<GardenDiseaseResponse> getDiseasesOfGarden(Long gardenId) {
        if (!gardenRepo.existsById(gardenId)) {
            throw new RuntimeException("Garden not found");
        }

        return gardenDiseaseRepository.findByGardenId(gardenId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public GardenDiseaseResponse getGardenDiseaseById(Long id) {
        GardenDisease gd = gardenDiseaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Garden disease not found"));

        return mapToResponse(gd);
    }

    public List<GardenDiseaseResponse> deleteGardenDisease(Long id) {
        GardenDisease gd = gardenDiseaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Garden disease not found"));

        Long gardenId = gd.getGarden().getId();


        gardenDiseaseRepository.delete(gd);

        return gardenDiseaseRepository.findByGardenId(gardenId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ================================================================
    // 🪴 UPDATE GARDEN
    // ================================================================
    @Transactional
    public GardenResponse updateGarden(Long gardenId, GardenUpdateRequest req) {

        User user = getCurrentUser();
        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        validateOwner(garden, user);

        // nickname change
        if (req.getNickname() != null) {
            String newName = req.getNickname().trim();
            if (!newName.equals(garden.getNickname())) {
                garden.setNickname(generateNickname(user, newName, null));
            }
        }

        Optional.ofNullable(req.getStatus()).ifPresent(garden::setStatus);
        Optional.ofNullable(req.getType()).ifPresent(garden::setType);
        Optional.ofNullable(req.getPotType()).ifPresent(garden::setPotType);

        gardenRepo.save(garden);
        return toResponse(garden);
    }

    // ================================================================
    // ❌ DELETE GARDEN
    // ================================================================
    @Transactional
    public void removePlantFromGarden(Long gardenId) {
        User user = getCurrentUser();

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        validateOwner(garden, user);
        gardenRepo.delete(garden);
    }

    // ================================================================
    // 📓 DIARY
    // ================================================================
    @Transactional
    public GardenResponse addDiaryEntry(Long gardenId, AddDiaryRequest req) {

        User user = getCurrentUser();
        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        validateOwner(garden, user);

        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diary content cannot be empty");
        }

        Diary diary = new Diary();
        diary.setGarden(garden);
        diary.setContent(req.getContent().trim());
        diary.setEntryTime(
                Optional.ofNullable(req.getEntryTime()).orElse(LocalDate.now())
        );

        diaryRepo.save(diary);

        garden = gardenRepo.findById(gardenId).orElseThrow();
        return toResponse(garden);
    }


    @Transactional
    public void removeDiaryEntry(Long diaryId) {
        User user = getCurrentUser();

        Diary diary = diaryRepo.findById(diaryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        validateOwner(diary.getGarden(), user);

        diaryRepo.delete(diary);
    }

    public List<DiaryResponse> getDiariesByGardenId(Long id) {

        Garden garden = gardenRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Garden not found"));

        validateOwner(garden, getCurrentUser());

        return diaryRepo.findByGardenId(id)
                .stream().map(this::toDiaryResponse)
                .collect(Collectors.toList());
    }

    // ================================================================
    // 🧩 MAPPERS
    // ================================================================
    private DiaryResponse toDiaryResponse(Diary d) {

        DiaryResponse res = new DiaryResponse();
        res.setId(d.getId());
        res.setGardenId(d.getGarden().getId());
        res.setEntryTime(d.getEntryTime());
        res.setContent(d.getContent());
        return res;
    }


    private GardenResponse toResponse(Garden g) {

        GardenResponse res = new GardenResponse();
        res.setId(g.getId());
        res.setNickname(g.getNickname());
        res.setPlant(g.getPlant());
        res.setStatus(g.getStatus());
        res.setDateAdded(g.getDateAdded());
        res.setType(g.getType());
        res.setPotType(g.getPotType());

        List<GardenDiseaseResponse> diseaseResponses =
                g.getGardenDiseases().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList());

        res.setDiseases(diseaseResponses);

        res.setDiseaseStatuses(
                diseaseResponses.stream().collect(
                        Collectors.toMap(
                                GardenDiseaseResponse::getDiseaseId,
                                GardenDiseaseResponse::getStatus
                        )
                )
        );

        res.setDetectedDates(
                diseaseResponses.stream().collect(
                        Collectors.toMap(
                                GardenDiseaseResponse::getDiseaseId,
                                GardenDiseaseResponse::getDetectedDate
                        )
                )
        );

        res.setDiaries(
                g.getDiaries().stream()
                        .map(this::toDiaryResponse)
                        .collect(Collectors.toList())
        );

        return res;
    }


    private GardenDiseaseResponse mapToResponse(GardenDisease gd) {
        return GardenDiseaseResponse.builder()
                .gardenDiseaseId(gd.getId())
                .diseaseId(gd.getDisease().getId())
                .name(gd.getDisease().getName())
                .scientificName(gd.getDisease().getScientificName())
                .detectedDate(gd.getDetectedDate())
                .curedDate(gd.getCuredDate())
                .status(gd.getStatus())
                .build();
    }

    // ================================================================
    // 🧰 HELPERS
    // ================================================================
    private LocalDateTime parseDateTime(String value, String errorMessage) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }

    private String generateNickname(User user, String requested, String defaultBase) {
        String base = (requested == null || requested.isBlank())
                ? Optional.ofNullable(defaultBase).orElse("My Plant")
                : requested.trim();

        String nickname = base;
        int count = 1;

        while (gardenRepo.existsByUserAndNickname(user, nickname)) {
            nickname = base + " (" + count++ + ")";
        }
        return nickname;
    }
}

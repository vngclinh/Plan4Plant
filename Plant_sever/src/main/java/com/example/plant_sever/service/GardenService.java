package com.example.plant_sever.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.plant_sever.DAO.DiseaseRepo;
import com.example.plant_sever.DTO.GardenResponse;
import com.example.plant_sever.DTO.GardenUpdateRequest;
import com.example.plant_sever.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.AddDiaryRequest;
import com.example.plant_sever.DTO.AddGardenRequest;
import com.example.plant_sever.DTO.DiaryResponse;
import com.example.plant_sever.DAO.DiaryRepo;

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

    @Transactional
    public GardenResponse addPlantToGarden(AddGardenRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Plant plant = plantRepo.findById(request.getPlantId())
                .orElseThrow(() -> new RuntimeException("Plant not found"));

        Garden garden = new Garden();
        garden.setUser(user);
        garden.setPlant(plant);

        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            garden.setNickname(request.getNickname());
        } else {
            String uniqueNickname = generateUniqueNickname(user, plant.getCommonName());
            garden.setNickname(uniqueNickname);
        }

        garden.setType(request.getType() != null ? request.getType() : GardenType.Indoor);
        garden.setPotType(request.getPotType() != null ? request.getPotType() : PotType.MEDIUM);
        garden.setStatus(request.getStatus() != null ? request.getStatus() : GardenStatus.ALIVE);
        garden.setDateAdded(LocalDateTime.now());

        garden.setDiseases(new ArrayList<>());
        if (request.getDiseaseIds() != null && !request.getDiseaseIds().isEmpty()) {
            List<Disease> diseases = diseaseRepo.findAllById(request.getDiseaseIds());
            garden.getDiseases().addAll(diseases);
        }

        return toResponse(gardenRepo.save(garden));
    }

    public List<GardenResponse> getUserGarden() {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Garden> gardens = gardenRepo.findByUserId(user.getId());
        return gardens.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public GardenResponse updateGarden(Long gardenId, GardenUpdateRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }

        if (request.getNickname() != null) {
            String newNickname = request.getNickname().trim();
            if (!newNickname.equals(garden.getNickname())) {
                newNickname = generateUniqueNickname(user, newNickname);
                garden.setNickname(newNickname);
            }
        }

        if (request.getStatus() != null) garden.setStatus(request.getStatus());
        if (request.getType() != null) garden.setType(request.getType());
        if (request.getPotType() != null) garden.setPotType(request.getPotType());

        if (request.getDiseaseIds() != null) {
            List<Disease> diseases = request.getDiseaseIds().isEmpty() ?
                    new ArrayList<>() :
                    diseaseRepo.findAllById(request.getDiseaseIds());
            garden.setDiseases(diseases);
        }

        return toResponse(gardenRepo.save(garden));
    }

    @Transactional
    public void removePlantFromGarden(Long gardenId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }

        gardenRepo.delete(garden);
    }
    @Transactional
    public GardenResponse addDiaryEntry(Long gardenId, AddDiaryRequest request) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không phải cây của bạn");
        }

        Diary newEntry = new Diary();
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung nhật ký không được để trống");
        }
        
        newEntry.setContent(request.getContent().trim());
        
        newEntry.setEntryTime(request.getEntryTime() != null ? request.getEntryTime() : LocalDate.now());

        // 3. Thiết lập liên kết ngược (RẤT QUAN TRỌNG) và lưu
        newEntry.setGarden(garden); 
        diaryRepo.save(newEntry);

        return toResponse(garden); 
    }
    @Transactional
    public void removeDiaryEntry(Long diaryId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Diary diaryEntry = diaryRepo.findById(diaryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mục nhật ký không tồn tại"));

        if (!diaryEntry.getGarden().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa nhật ký này");
        }

        diaryRepo.delete(diaryEntry);
    }

    private String generateUniqueNickname(User user, String baseNickname) {
        if (baseNickname == null || baseNickname.isBlank()) {
            baseNickname = "My Plant";
        }

        String nickname = baseNickname.trim();
        int counter = 1;

        while (gardenRepo.existsByUserAndNickname(user, nickname)) {
            nickname = baseNickname + " (" + counter + ")";
            counter++;
        }

        return nickname;
    }

    public List<DiaryResponse> getDiariesByGardenId(Long gardenId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cây trồng không tồn tại"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền truy cập nhật ký của cây này");
        }

        List<Diary> diaries = diaryRepo.findByGardenId(gardenId);

        return diaries.stream()
                .map(this::toDiaryResponse)
                .collect(Collectors.toList());
    }

    private DiaryResponse toDiaryResponse(Diary diary) {
        DiaryResponse response = new DiaryResponse();
        response.setId(diary.getId());
        response.setGardenId(diary.getGarden().getId()); 
        response.setEntryTime(diary.getEntryTime());
        response.setContent(diary.getContent());
        return response;
    }
    private GardenResponse toResponse(Garden garden) {
        GardenResponse response = new GardenResponse();
        response.setId(garden.getId());
        response.setNickname(garden.getNickname());
        response.setPlant(garden.getPlant());
        response.setStatus(garden.getStatus());
        response.setDateAdded(garden.getDateAdded());
        response.setType(garden.getType());
        response.setPotType(garden.getPotType());
        response.setDiseaseNames(garden.getDiseases().stream()
                .map(Disease::getName)
                .collect(Collectors.toList()));
        response.setDiaries(garden.getDiaries().stream()
            .map(this::toDiaryResponse) 
            .collect(Collectors.toList()));
        return response;
    }
}

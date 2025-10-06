package com.example.plant_sever.service;

import com.example.plant_sever.DAO.DiseaseRepo;
import com.example.plant_sever.model.Disease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiseaseService {

    private final DiseaseRepo diseaseRepository;

    public List<Disease> getAllDiseases() {
        return diseaseRepository.findAll();
    }

    public Disease getDiseaseById(Long id) {
        return diseaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Disease not found with id: " + id));
    }

    public List<Disease> searchDiseases(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return diseaseRepository.findAll();
        }
        return diseaseRepository.searchByName(keyword);
    }
}


package com.example.plant_sever.DAO;

import com.example.plant_sever.model.GardenDisease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GardenDiseaseRepo extends JpaRepository<GardenDisease, Long> {
    List<GardenDisease> findByGardenId(Long gardenId);
}

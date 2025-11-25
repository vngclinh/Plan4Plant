package com.example.plant_sever.DAO;

import com.example.plant_sever.model.GardenDisease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GardenDiseaseRepo extends JpaRepository<GardenDisease, Long> {

}

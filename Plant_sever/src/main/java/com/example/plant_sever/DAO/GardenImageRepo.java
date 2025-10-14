package com.example.plant_sever.DAO;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.plant_sever.model.GardenImage;

@Repository
public interface GardenImageRepo extends JpaRepository<GardenImage, Long> {
    List<GardenImage> findByGardenId(Long gardenId);
}

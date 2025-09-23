package com.example.plant_sever.DAO;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.Plant;
import com.example.plant_sever.model.User;


@Repository
public interface GardenRepo extends JpaRepository<Garden, Long> {
    List<Garden> findByUserId(Long userId);
    boolean existsByUserAndPlant(User user, Plant plant);
}

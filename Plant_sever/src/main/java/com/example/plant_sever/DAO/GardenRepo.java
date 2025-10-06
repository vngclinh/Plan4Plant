package com.example.plant_sever.DAO;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.Plant;
import com.example.plant_sever.model.User;


@Repository
public interface GardenRepo extends JpaRepository<Garden, Long> {
    @Query("SELECT DISTINCT g FROM Garden g " +
            "LEFT JOIN FETCH g.diseases " +
            "WHERE g.user.id = :userId")
    List<Garden> findByUserId(@Param("userId") Long userId);
    boolean existsByUserAndPlant(User user, Plant plant);
}

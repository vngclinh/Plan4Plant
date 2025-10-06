package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Plant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface PlantRepo extends JpaRepository<Plant, Long> {

    @Query("SELECT DISTINCT p FROM Plant p " +
            "LEFT JOIN FETCH p.diseases " +
            "WHERE LOWER(p.commonName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.scientificName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.family) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.genus) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.species) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Plant> searchPlants(@Param("keyword") String keyword);
}

package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Plant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface PlantRepo extends JpaRepository<Plant, Long> {
    @Query(value = "SELECT * FROM plant p " +
            "WHERE LOWER(IFNULL(p.common_name, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(IFNULL(p.other_names, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(IFNULL(p.scientific_name, '')) LIKE CONCAT('%', :keyword, '%')",
            nativeQuery = true)
    List<Plant> searchByName(@Param("keyword") String keyword);

}

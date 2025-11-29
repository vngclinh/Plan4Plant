package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Disease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiseaseRepo extends JpaRepository<Disease, Long> {
    @Query("SELECT d FROM Disease d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Disease> searchByName(@Param("keyword") String keyword);
    @Query("""
        SELECT d FROM Disease d
        WHERE 
            LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(d.scientificName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(d.symptoms) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(d.causes) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY d.priority DESC
    """)
    List<Disease> fuzzySearch(@Param("keyword") String keyword);
}

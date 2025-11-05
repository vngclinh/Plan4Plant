package com.example.plant_sever.DAO;

import com.example.plant_sever.model.WeatherData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
@Repository
public interface WeatherRepo extends JpaRepository<WeatherData, Long> {
    List<WeatherData> findByRegionKeyAndDateBetween(String regionKey, LocalDate start, LocalDate end);
    boolean existsByRegionKeyAndDate(String regionKey, LocalDate date);


}

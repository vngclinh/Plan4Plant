package com.example.plant_sever.service;


import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DAO.WeatherRepo;
import com.example.plant_sever.DTO.WeatherForecastDTO;
import com.example.plant_sever.model.User;
import com.example.plant_sever.model.WeatherData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherUpdateService {

    private final MeteosourceClient meteosourceClient;
    private final UserRepo userRepository;
    private final WeatherRepo weatherDataRepository;

    /**
     * Run every day at 0:00 AM
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void updateDailyWeather() {
        log.info("🌤 Starting daily weather update...");


        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getLat() != null && u.getLon() != null)
                .collect(Collectors.toList());


        Map<String, List<User>> regionGroups = users.stream()
                .collect(Collectors.groupingBy(u -> {
                    double regionLat = Math.floor(u.getLat() * 20) / 20; // ~5 km per region
                    double regionLon = Math.floor(u.getLon() * 20) / 20;
                    return String.format(Locale.US, "%.2f,%.2f", regionLat, regionLon);
                }));

        LocalDate today = LocalDate.now();

        // Step 3: iterate over each region and fetch current weather once
        for (Map.Entry<String, List<User>> entry : regionGroups.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(",");
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            if (weatherDataRepository.existsByRegionKeyAndDate(key, today)) {
                log.info("⏭ Weather for region {} already exists for {}, skipping...", key, today);
                continue;
            }


            try {
                WeatherForecastDTO current = meteosourceClient.getCurrentWeather(lat, lon);

                if (current == null) {
                    log.warn("⚠️ No weather data for region {}", key);
                    continue;
                }

                // Ensure null safety for temperature values
                Double tempMax = current.getMaxTemperature() != null ? current.getMaxTemperature() : current.getMinTemperature();
                Double tempMin = current.getMinTemperature() != null ? current.getMinTemperature() : current.getMaxTemperature();

                if (tempMax == null || tempMin == null || current.getPrecipitation() == 0.0) {
                    log.info("ℹ️ Incomplete weather data for region {}, skipping...", key);
                    continue;
                }

                WeatherData data = WeatherData.builder()
                        .regionKey(key)
                        .date(today)
                        .precipitationMm(current.getPrecipitation())
                        .temperatureMax(tempMax)
                        .temperatureMin(tempMin)
                        .build();

                weatherDataRepository.save(data);

                log.info("✅ Saved weather for region {} (tempMax={}, rain={})",
                        key, tempMax, current.getPrecipitation());

            } catch (Exception e) {
                log.error("❌ Failed to update region {}: {}", key, e.getMessage(), e);
            }
        }

        log.info("🌦 Weather update completed successfully.");
    }
}
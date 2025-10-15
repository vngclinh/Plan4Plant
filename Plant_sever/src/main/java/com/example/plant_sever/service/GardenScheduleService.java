package com.example.plant_sever.service;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.GardenScheduleRepo;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DAO.WeatherRepo;
import com.example.plant_sever.DTO.GardenScheduleRequest;
import com.example.plant_sever.DTO.GardenScheduleResponse;
import com.example.plant_sever.DTO.WeatherForecastDTO;
import com.example.plant_sever.Security.JwtUtils;
import com.example.plant_sever.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GardenScheduleService {

    private final GardenScheduleRepo scheduleRepository;
    private final GardenRepo gardenRepository;
    private final MeteosourceClient meteosourceClient;
    private final WeatherRepo weatherRepository;
    private final JwtUtils jwtUtils;
    private final UserRepo userRepo;

    public boolean existsSchedule(Long gardenId, String scheduledTimeStr) {
        LocalDateTime scheduledTime;
        try {
            scheduledTime = LocalDateTime.parse(scheduledTimeStr);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Invalid datetime format");
        }

        // Use findSchedulesBetween with start = scheduledTime, end = scheduledTime
        List<GardenSchedule> schedules = scheduleRepository.findSchedulesBetween(
                gardenId,
                scheduledTime,
                scheduledTime
        );

        return !schedules.isEmpty();
    }

    public GardenScheduleResponse create(GardenScheduleRequest request) {
        Garden garden = gardenRepository.findById(request.getGardenId())
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        GardenSchedule schedule;
        schedule = GardenSchedule.builder()
                    .garden(garden)
                    .type(request.getType())
                    .scheduledTime(request.getScheduledTime())
                    .completion(request.getCompletion() != null ? request.getCompletion() : Completion.NotDone)
                    .note(request.getNote())
                    .waterAmount(request.getWaterAmount())
                    .fertilityAmount(request.getFertilityAmount())
                    .fertilityType(request.getFertilityType())
                    .build();

        return toResponse(scheduleRepository.save(schedule));
    }

    public GardenScheduleResponse update(Long id, GardenScheduleRequest request) {
        GardenSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));

        if (request.getType() != null) schedule.setType(request.getType());
        if (request.getScheduledTime() != null) schedule.setScheduledTime(request.getScheduledTime());
        if (request.getCompletion() != null) schedule.setCompletion(request.getCompletion());
        if (request.getNote() != null) schedule.setNote(request.getNote());

        return toResponse(scheduleRepository.save(schedule));
    }

    public void delete(Long id) {
        scheduleRepository.deleteById(id);
    }

    public GardenScheduleResponse getById(Long id) {
        return scheduleRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
    }

    public List<GardenScheduleResponse> getByGarden(Long gardenId) {
        return scheduleRepository.findByGarden_Id(gardenId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GardenScheduleResponse> getAll(User user) {
        return scheduleRepository.findByGarden_User(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GardenScheduleResponse> getByGardenAndDate(Long gardenId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        return scheduleRepository.findSchedulesBetween(gardenId, startOfDay, endOfDay)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }


    private GardenScheduleResponse toResponse(GardenSchedule schedule) {
        Garden garden = schedule.getGarden();
        Plant plant = garden.getPlant();

        return GardenScheduleResponse.builder()
                .id(schedule.getId())
                .gardenId(garden.getId())
                .gardenNickname(garden.getNickname())
                .plantId(plant != null ? plant.getId() : null)
                .plantName(plant != null ? plant.getCommonName() : null)
                .type(schedule.getType())
                .scheduledTime(schedule.getScheduledTime())
                .completion(schedule.getCompletion())
                .note(schedule.getNote())
                .waterAmount(schedule.getWaterAmount())
                .fertilityType(schedule.getFertilityType())
                .fertilityAmount(schedule.getFertilityAmount())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }



    public List<GardenScheduleResponse> getByCompletion(Completion completion) {
        return scheduleRepository.findByCompletion(completion)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GardenScheduleResponse> generateWeeklyWateringSchedule(Long gardenId, double lat, double lon) {
        Garden garden = gardenRepository.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        Plant plant = garden.getPlant();
        if (plant == null) throw new RuntimeException("No plant associated with this garden");

        boolean isOutdoor = garden.getType() == GardenType.Outdoor;
        double potAreaM2 = garden.getPotType() != null ? garden.getPotType().getArea() : 0.01;

        // ---- NULL-SAFE: interval ----
        int minInterval = plant.getMinInterval() != null ? plant.getMinInterval() : 7;
        int maxInterval = plant.getMaxInterval() != null ? plant.getMaxInterval() : minInterval;

        // ---- NULL-SAFE: plant water amount ----
        double plantWaterMl = plant.getWaterAmount() != null ? plant.getWaterAmount() : 0.6;

        // ---- NULL-SAFE: plant temp threshold ----
        Double pTmax = plant.getMaxTemperature();
        Double pTmin = plant.getMinTemperature();

        LocalDate today = LocalDate.now();
        LocalDateTime periodStart = today.minusDays(maxInterval).atStartOfDay();

        double roundedLat = Math.round(lat * 100.0) / 100.0;
        double roundedLon = Math.round(lon * 100.0) / 100.0;
        String regionKey = roundedLat + "," + roundedLon;

        // --- Find last watering ---
        List<GardenSchedule> recentSchedules = scheduleRepository.findSchedulesBetween(
                gardenId, periodStart, today.atTime(23, 59)
        );

        LocalDateTime lastWatered = null;
        if (recentSchedules != null && !recentSchedules.isEmpty()) {
            lastWatered = recentSchedules.stream()
                    .filter(s -> s.getType() == ScheduleType.WATERING && s.getCompletion() != Completion.Skipped)
                    .map(GardenSchedule::getScheduledTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }
        System.out.println("=== Last Watered: " + lastWatered + " ===");

        LocalDateTime nextPossibleWatering = (lastWatered != null)
                ? lastWatered.plusDays(minInterval)
                : today.atStartOfDay();

        // Historical rain for outdoor
        double cumulativeRain = 0;
        if (isOutdoor && lastWatered != null) {
            LocalDate startDate = lastWatered.toLocalDate().plusDays(1);
            List<WeatherData> rains = weatherRepository.findByRegionKeyAndDateBetween(regionKey, startDate, today);

            if (rains != null && !rains.isEmpty()) {
                cumulativeRain = rains.stream()
                        .mapToDouble(w -> (w.getPrecipitationMm() != null ? w.getPrecipitationMm() : 0.0) * potAreaM2 * 1000)
                        .sum();
            }
        }
        System.out.println("Initial cumulative rain: " + cumulativeRain + " ml");

        List<GardenScheduleResponse> results = new ArrayList<>();

        // Forecast next 7 days
        List<WeatherForecastDTO> forecast = meteosourceClient.get7DayForecast(lat, lon);
        LocalDateTime forecastEnd = today.plusDays(6).atTime(23, 59);

        // Existing schedules in forecast window
        List<GardenSchedule> existingSchedules = scheduleRepository.findSchedulesBetween(
                gardenId, lastWatered != null ? lastWatered : today.atStartOfDay(), forecastEnd
        );

        for (WeatherForecastDTO day : forecast) {
            LocalDate forecastDate = day.getDate();

            // ---- NULL-SAFE: forecast fields ----
            double dayPrecip = day.getPrecipitation() != null ? day.getPrecipitation() : 0.0;
            Double dayTmaxObj = day.getMaxTemperature();
            Double dayTminObj = day.getMinTemperature();

            // Determine if minInterval is satisfied
            if (forecastDate.atStartOfDay().isBefore(nextPossibleWatering)) {
                if (isOutdoor) {
                    double rainMl = dayPrecip * potAreaM2 * 1000;
                    double scheduledWaterMl = existingSchedules.stream()
                            .filter(s -> s.getType() == ScheduleType.WATERING &&
                                    s.getScheduledTime().toLocalDate().equals(forecastDate))
                            .mapToDouble(s -> s.getWaterAmount() != null ? s.getWaterAmount() : 0.0)
                            .sum();
                    cumulativeRain += rainMl + scheduledWaterMl;
                }
                System.out.println(forecastDate + ": minInterval not reached. Cumulative rain: " + cumulativeRain + " ml");
                continue;
            }

            double waterNeeded;
            if (isOutdoor) {
                double rainMl = dayPrecip * potAreaM2 * 1000;
                double scheduledWaterMl = existingSchedules.stream()
                        .filter(s -> s.getType() == ScheduleType.WATERING &&
                                s.getScheduledTime().toLocalDate().equals(forecastDate))
                        .mapToDouble(s -> s.getWaterAmount() != null ? s.getWaterAmount() : 0.0)
                        .sum();
                double totalRain = rainMl + scheduledWaterMl;

                System.out.println(forecastDate + ": cumulativeRain before watering: " + cumulativeRain + " ml, forecast rain: " + rainMl + " ml, scheduled water: " + scheduledWaterMl + " ml");

                waterNeeded = Math.max(0, plantWaterMl - cumulativeRain);

                if (waterNeeded > 0) {
                    GardenSchedule schedule = GardenSchedule.builder()
                            .garden(garden)
                            .type(ScheduleType.WATERING)
                            .scheduledTime(forecastDate.atTime(8, 0))
                            .completion(Completion.NotDone)
                            .waterAmount(waterNeeded)
                            .note("Auto-generated outdoor watering (" + waterNeeded + " ml)")
                            .build();
                    scheduleRepository.save(schedule);
                    results.add(toResponse(schedule));

                    cumulativeRain -= waterNeeded;
                    if (cumulativeRain < 0) cumulativeRain = 0;
                } else {
                    cumulativeRain += totalRain;
                }

                System.out.println(forecastDate + ": waterScheduled: " + waterNeeded + " ml, cumulativeRain after watering: " + cumulativeRain + " ml");

            } else {
                // Indoor
                waterNeeded = plantWaterMl;
                GardenSchedule schedule = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.WATERING)
                        .scheduledTime(forecastDate.atTime(8, 0))
                        .completion(Completion.NotDone)
                        .waterAmount(waterNeeded)
                        .note("Auto-generated indoor watering (" + waterNeeded + " ml)")
                        .build();
                scheduleRepository.save(schedule);
                results.add(toResponse(schedule));

                System.out.println(forecastDate + ": indoor waterScheduled: " + waterNeeded + " ml");
            }

            // Update last watered and next possible watering
            lastWatered = forecastDate.atStartOfDay();
            nextPossibleWatering = lastWatered.plusDays(minInterval);

            // ---- MIST LOGIC (NULL-SAFE) ----
            // Chỉ xét khi cả ngưỡng của cây (pTmax, pTmin) đều có
            if (pTmax != null && pTmin != null && dayTmaxObj != null) {
                double threshold = (pTmax + pTmin) / 2.0;
                if (dayTmaxObj > threshold) {
                    GardenSchedule noonMist = GardenSchedule.builder()
                            .garden(garden)
                            .type(ScheduleType.MIST)
                            .scheduledTime(forecastDate.atTime(12, 0))
                            .completion(Completion.NotDone)
                            .note("Auto-generated mist due to high temperature (" + dayTmaxObj + "°C)")
                            .build();
                    scheduleRepository.save(noonMist);
                    results.add(toResponse(noonMist));

                    GardenSchedule eveningMist = GardenSchedule.builder()
                            .garden(garden)
                            .type(ScheduleType.MIST)
                            .scheduledTime(forecastDate.atTime(18, 0))
                            .completion(Completion.NotDone)
                            .note("Auto-generated mist due to high temperature (" + dayTmaxObj + "°C)")
                            .build();
                    scheduleRepository.save(eveningMist);
                    results.add(toResponse(eveningMist));
                }
            } else {
                // Không đủ dữ liệu nhiệt độ → bỏ qua mist cho ngày này
                if (dayTmaxObj == null) {
                    System.out.println(forecastDate + ": skip mist (forecast max temp is null)");
                }
            }
        }

        return results;
    }

    public List<GardenScheduleResponse> getUserSchedulesByDate(String token, LocalDate date) {
        // 🔹 Clean token
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 🔹 Extract username from JWT
        String username = jwtUtils.getUsernameFromJwt(token);

        // 🔹 Find the user by username
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 🔹 Define date range (start and end of selected day)
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        // 🔹 Query schedules for that user's gardens
        List<GardenSchedule> schedules = scheduleRepository.findByUserAndDate(
                user.getId(), startOfDay, endOfDay);

        // 🔹 Convert to response
        return schedules.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

}

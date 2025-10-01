package com.example.plant_sever.service;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.GardenScheduleRepo;

import com.example.plant_sever.DAO.WeatherRepo;
import com.example.plant_sever.DTO.GardenScheduleRequest;
import com.example.plant_sever.DTO.GardenScheduleResponse;
import com.example.plant_sever.DTO.WeatherForecastDTO;
import com.example.plant_sever.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public GardenScheduleResponse create(GardenScheduleRequest request) {
        Garden garden = gardenRepository.findById(request.getGardenId())
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        // check if schedule already exists for same garden + time
        Optional<GardenSchedule> existing = scheduleRepository.findByGardenAndScheduledTime(
                garden, request.getScheduledTime()
        );

        GardenSchedule schedule;
        if (existing.isPresent()) {
            // overwrite
            schedule = existing.get();
            schedule.setType(request.getType());
            schedule.setCompletion(request.getCompletion() != null ? request.getCompletion() : Completion.NotDone);
            schedule.setNote(request.getNote());
        } else {
            // create new
            schedule = GardenSchedule.builder()
                    .garden(garden)
                    .type(request.getType())
                    .scheduledTime(request.getScheduledTime())
                    .completion(request.getCompletion() != null ? request.getCompletion() : Completion.NotDone)
                    .note(request.getNote())
                    .build();
        }

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

    private GardenScheduleResponse toResponse(GardenSchedule schedule) {
        return GardenScheduleResponse.builder()
                .id(schedule.getId())
                .gardenId(schedule.getGarden().getId())
                .type(schedule.getType())
                .scheduledTime(schedule.getScheduledTime())
                .completion(schedule.getCompletion())
                .note(schedule.getNote())
                .waterAmount(schedule.getWaterAmount())
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

        LocalDate today = LocalDate.now();
        LocalDateTime periodStart = today.minusDays(plant.getMaxInterval()).atStartOfDay();

        // --- Find last watering ---
        List<GardenSchedule> recentSchedules = scheduleRepository.findSchedulesBetween(
                gardenId, periodStart, today.atTime(23, 59)
        );
        LocalDateTime lastWatered = recentSchedules.stream()
                .filter(s -> s.getType() == ScheduleType.WATERING && s.getCompletion() != Completion.Skipped)
                .map(GardenSchedule::getScheduledTime)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        System.out.println("=== Last Watered: " + lastWatered + " ===");

        LocalDateTime nextPossibleWatering = (lastWatered != null)
                ? lastWatered.plusDays(plant.getMinInterval())
                : today.atStartOfDay();

        // --- Calculate historical rain for outdoor gardens ---
        double cumulativeRain = 0;
        if (isOutdoor && lastWatered != null) {
            LocalDate startDate = lastWatered.toLocalDate().plusDays(1);
            cumulativeRain = weatherRepository.findByDateBetween(startDate, today)
                    .stream()
                    .mapToDouble(w -> w.getPrecipitationMm() * potAreaM2 * 1000)
                    .sum();
        }

        System.out.println("Initial cumulative rain: " + cumulativeRain + " ml");

        List<GardenScheduleResponse> results = new ArrayList<>();

        // --- Forecast next 7 days ---
        List<WeatherForecastDTO> forecast = meteosourceClient.get7DayForecast(lat, lon);
        LocalDateTime forecastEnd = today.plusDays(6).atTime(23, 59);

        // Existing schedules in forecast window
        List<GardenSchedule> existingSchedules = scheduleRepository.findSchedulesBetween(
                gardenId, lastWatered != null ? lastWatered : today.atStartOfDay(), forecastEnd
        );

        for (WeatherForecastDTO day : forecast) {
            LocalDate forecastDate = day.getDate();

            // --- Determine if minInterval is satisfied ---
            if (forecastDate.atStartOfDay().isBefore(nextPossibleWatering)) {
                if (isOutdoor) {
                    // Accumulate rainfall even if we can't water yet
                    double rainMl = day.getPrecipitation() * potAreaM2 * 1000;
                    double scheduledWaterMl = existingSchedules.stream()
                            .filter(s -> s.getType() == ScheduleType.WATERING &&
                                    s.getScheduledTime().toLocalDate().equals(forecastDate))
                            .mapToDouble(s -> s.getWaterAmount() != null ? s.getWaterAmount() : 0)
                            .sum();
                    cumulativeRain += rainMl + scheduledWaterMl;
                }
                System.out.println(forecastDate + ": minInterval not reached. Cumulative rain: " + cumulativeRain + " ml");
                continue;
            }

            double waterNeeded;
            if (isOutdoor) {
                // --- Outdoor: consider cumulative rain ---
                double rainMl = day.getPrecipitation() * potAreaM2 * 1000;
                double scheduledWaterMl = existingSchedules.stream()
                        .filter(s -> s.getType() == ScheduleType.WATERING &&
                                s.getScheduledTime().toLocalDate().equals(forecastDate))
                        .mapToDouble(s -> s.getWaterAmount() != null ? s.getWaterAmount() : 0)
                        .sum();
                double totalRain = rainMl + scheduledWaterMl;

                System.out.println(forecastDate + ": cumulativeRain before watering: " + cumulativeRain + " ml, forecast rain: " + rainMl + " ml, scheduled water: " + scheduledWaterMl + " ml");

                waterNeeded = Math.max(0, plant.getWaterAmount() - cumulativeRain);

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
                // --- Indoor: ignore rain, schedule full plantWaterAmount if minInterval passed ---
                waterNeeded = plant.getWaterAmount();
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
            nextPossibleWatering = lastWatered.plusDays(plant.getMinInterval());

            // --- Mist logic ---
            if (plant.getMaxTemperature() != null && day.getMaxTemperature() > plant.getMaxTemperature()) {
                GardenSchedule noonMist = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.MIST)
                        .scheduledTime(forecastDate.atTime(12, 0))
                        .completion(Completion.NotDone)
                        .note("Auto-generated mist due to high temperature (" + day.getMaxTemperature() + "°C)")
                        .build();
                scheduleRepository.save(noonMist);
                results.add(toResponse(noonMist));

                GardenSchedule eveningMist = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.MIST)
                        .scheduledTime(forecastDate.atTime(18, 0))
                        .completion(Completion.NotDone)
                        .note("Auto-generated mist due to high temperature (" + day.getMaxTemperature() + "°C)")
                        .build();
                scheduleRepository.save(eveningMist);
                results.add(toResponse(eveningMist));
            }
        }

        return results;
    }




}

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
import java.util.*;
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

        if (request.getWaterAmount() != null) schedule.setWaterAmount(request.getWaterAmount());
        if (request.getFertilityAmount() != null) schedule.setFertilityAmount(request.getFertilityAmount());
        if (request.getFertilityType() != null) schedule.setFertilityType(request.getFertilityType());
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

        double roundedLat = Math.round(lat * 20.0) / 20.0;
        double roundedLon = Math.round(lon * 20.0) / 20.0;
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

        // --- Calculate historical rain for outdoor gardens ---
        double cumulativeRain = 0;
        if (isOutdoor && lastWatered != null) {
            LocalDate startDate = lastWatered.toLocalDate().plusDays(1);
            List<WeatherData> rains = weatherRepository.findByRegionKeyAndDateBetween(regionKey, startDate, today);

            if (rains == null || rains.isEmpty()) {
                cumulativeRain = 0;
            } else {
                cumulativeRain = rains.stream()
                        .mapToDouble(w -> w.getPrecipitationMm() * potAreaM2 * 1000)
                        .sum();
            }
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

    public List<GardenScheduleResponse> generateDiseaseTreatmentSchedule(Long gardenId) {
        Garden garden = gardenRepository.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        List<Disease> diseases = garden.getDiseases();
        if (diseases.isEmpty()) {
            throw new RuntimeException("No disease detected for this garden");
        }

        // Sort diseases by priority (high first)
        diseases.sort(Comparator.comparingInt(Disease::getPriority).reversed());

        List<GardenScheduleResponse> responses = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0);

        int maxStopWateringDays = 0;
        Map<String, Integer> fungicideMap = new HashMap<>(); // <fungicideType, interval>
        Map<Disease, StringBuilder> pruningNotes = new HashMap<>();

        // --- Step 1: Analyze treatment rules ---
        for (Disease disease : diseases) {
            for (TreatmentRule rule : disease.getTreatmentRules()) {
                switch (rule.getType()) {
                    case STOP_WATERING -> {
                        maxStopWateringDays = Math.max(maxStopWateringDays, rule.getIntervalDays());
                    }
                    case FUNGICIDE -> {
                        fungicideMap.merge(rule.getFungicideType(), rule.getIntervalDays(), Integer::min);
                    }
                    case PRUNING -> {
                        // Merge multiple pruning actions for one disease
                        pruningNotes
                                .computeIfAbsent(disease, d -> new StringBuilder("Pruning actions for " + d.getName() + ": "))
                                .append(rule.getDescription() != null ? rule.getDescription() + "; " : "");
                    }
                    default -> {
                        // ignore others
                    }
                }
            }
        }

        // --- Step 2: Stop watering schedule ---
        LocalDateTime stopWaterEndTime = null;
        if (maxStopWateringDays > 0) {
            stopWaterEndTime = startTime.plusDays(maxStopWateringDays);

            // Move any watering events that fall inside stop-watering period
            List<GardenSchedule> wateringEvents = scheduleRepository
                    .findByGardenAndTypeAndScheduledTimeBetween(
                            garden,
                            ScheduleType.WATERING,
                            startTime,
                            stopWaterEndTime
                    );

            int offsetDays = 0;
            for (GardenSchedule water : wateringEvents) {
                LocalDateTime newTime = stopWaterEndTime.plusDays(offsetDays).withHour(8).withMinute(0);
                water.setScheduledTime(newTime);
                water.setNote((water.getNote() == null ? "" : water.getNote() + " ")
                        + "(rescheduled after stop-watering period)");
                scheduleRepository.save(water);

                offsetDays += 3;
            }

            for (int i = 0; i < maxStopWateringDays; i++) {
                GardenSchedule stop = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.STOP_WATERING)
                        .scheduledTime(startTime.plusDays(i))
                        .note("Stop watering (Day " + (i + 1) + "/" + maxStopWateringDays + ") due to multiple diseases")
                        .build();
                scheduleRepository.save(stop);
                responses.add(toResponse(stop));
            }

        }

        // 🪴 Determine where to start the fungicide schedule
        LocalDateTime lastFungicideTime = startTime;


        GardenSchedule lastFungicideOverall = (GardenSchedule) scheduleRepository
                .findTopByGardenAndTypeAndScheduledTimeLessThanEqualOrderByScheduledTimeDesc(
                        garden, ScheduleType.FUNGICIDE, LocalDateTime.now()
                )
                .orElse(null);

        if (lastFungicideOverall != null) {
            lastFungicideTime = lastFungicideOverall.getScheduledTime();
        }

// 🧩 Sequentially create fungicide schedules based on fungicideMap order
        for (Map.Entry<String, Integer> entry : fungicideMap.entrySet()) {
            String fungicideType = entry.getKey();
            int intervalDays = entry.getValue();

            // 👉 Sequential scheduling: each fungicide happens after the previous one
            LocalDateTime nextScheduleTime = lastFungicideTime.plusDays(intervalDays)
                    .withHour(8).withMinute(0);

            LocalDateTime fungicideEnd = nextScheduleTime.plusDays(intervalDays);

            // 🚫 Move any fertilizing events that conflict with this fungicide period
            List<GardenSchedule> fertilizingEvents = scheduleRepository
                    .findByGardenAndTypeAndScheduledTimeBetween(
                            garden,
                            ScheduleType.FERTILIZING,
                            nextScheduleTime,
                            fungicideEnd
                    );

            int offsetDays = 0;
            for (GardenSchedule fertilize : fertilizingEvents) {
                LocalDateTime newTime = fungicideEnd.plusDays(offsetDays)
                        .withHour(8).withMinute(0);
                fertilize.setScheduledTime(newTime);
                fertilize.setNote((fertilize.getNote() == null ? "" : fertilize.getNote() + " ")
                        + "(rescheduled due to fungicide treatment)");
                scheduleRepository.save(fertilize);
                offsetDays += 3; // keep 3 days apart between each rescheduled fertilizing
            }

            // 💾 Create and save the fungicide schedule
            GardenSchedule fungicide = GardenSchedule.builder()
                    .garden(garden)
                    .type(ScheduleType.FUNGICIDE)
                    .fungicideType(fungicideType)
                    .scheduledTime(nextScheduleTime)
                    .note("Apply " + fungicideType + " every " + intervalDays + " days for related diseases")
                    .build();

            scheduleRepository.save(fungicide);
            responses.add(toResponse(fungicide));

            // 🔁 Update for the next fungicide in sequence
            lastFungicideTime = nextScheduleTime;
        }
        // --- Step 4: Pruning schedules (merged per disease) ---
        for (Map.Entry<Disease, StringBuilder> entry : pruningNotes.entrySet()) {
            Disease disease = entry.getKey();
            String note = entry.getValue().toString();

            GardenSchedule pruning = GardenSchedule.builder()
                    .garden(garden)
                    .type(ScheduleType.PRUNING)
                    .scheduledTime(startTime)
                    .note(note)
                    .build();

            scheduleRepository.save(pruning);
            responses.add(toResponse(pruning));
        }

        return responses;
    }
}
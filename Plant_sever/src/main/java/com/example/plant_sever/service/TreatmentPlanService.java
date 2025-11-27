package com.example.plant_sever.service;

import com.example.plant_sever.DAO.DiseaseRepo;
import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.GardenScheduleRepo;
import com.example.plant_sever.DTO.TreatmentPlanAction;
import com.example.plant_sever.DTO.TreatmentPlanResult;
import com.example.plant_sever.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TreatmentPlanService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int PREVIEW_DAYS = 14;

    private final GardenRepo gardenRepository;
    private final GardenScheduleRepo scheduleRepository;
    private final DiseaseRepo diseaseRepository;

// Trong file TreatmentPlanService.java

    public TreatmentPlanResult previewTreatmentPlan(Long userId,
                                                    String plantName,
                                                    String diseaseName,
                                                    String gardenNickname,
                                                    Long gardenId) {
        try {
            // 1. Kiểm tra thông tin người dùng và tên cây (Bắt buộc phải có tên cây)
            if (userId == null) {
                return TreatmentPlanResult.builder().success(false).message("Thiếu thông tin người dùng.").build();
            }
            if (plantName == null || plantName.isBlank()) {
                return TreatmentPlanResult.builder().success(false).message("Vui lòng cung cấp tên cây hoặc biệt danh cây trong vườn.").build();
            }

            // 2. Tìm kiếm Cây trong vườn (Ưu tiên Nickname -> Common Name)
            Garden garden = resolveGarden(userId, plantName, gardenNickname, gardenId);
            
            // Trường hợp KHÔNG tìm thấy cây -> Báo lỗi ngay
            if (garden == null) {
                return TreatmentPlanResult.builder()
                        .success(false)
                        .message("Không tìm thấy cây nào tên là '" + plantName + "' trong vườn của bạn. Bạn đã thêm cây này vào ứng dụng chưa?")
                        .build();
            }

            // 3. Xử lý trường hợp THIẾU TÊN BỆNH (Đây là chỗ sửa lỗi "haha...")
            // Nếu tìm thấy cây nhưng người dùng chưa nói bệnh -> Trả về Success=True để Gemini biết là có cây
            // Kèm theo câu hỏi gợi ý để Gemini hỏi lại người dùng.
            if (diseaseName == null || diseaseName.isBlank()) {
                return TreatmentPlanResult.builder()
                        .success(true) 
                        .message("Đã tìm thấy cây **" + friendlyGardenName(garden) + "** (ID: " + garden.getId() + ").\n"
                                + "Cây này đang bị bệnh gì vậy ạ? Hãy mô tả triệu chứng để tôi lên lịch xử lý.")
                        .gardenId(garden.getId())
                        .gardenNickname(garden.getNickname())
                        .plantName(garden.getPlant() != null ? garden.getPlant().getCommonName() : null)
                        .build();
            }

            // 4. Nếu có tên bệnh -> Tìm thông tin bệnh trong DB
            Disease disease = resolveDisease(diseaseName);
            if (disease == null) {
                return TreatmentPlanResult.builder()
                        .success(false)
                        .message("Tìm thấy cây **" + friendlyGardenName(garden) + "** nhưng hệ thống chưa nhận diện được bệnh '" + diseaseName + "'. Vui lòng mô tả khác đi (ví dụ: đốm lá, thối rễ...).")
                        .gardenId(garden.getId())
                        .build();
            }

            // 5. Có đủ Cây và Bệnh -> Tính toán lịch trình (Logic cũ)
            List<TreatmentPlanAction> current = mapCurrentSchedule(garden);
            List<TreatmentPlanAction> proposed = buildProposedActions(garden, disease, false);

            String message = proposed.isEmpty()
                    ? String.format("Không tìm thấy hướng xử lý cụ thể cho bệnh %s của %s. Hãy kiểm tra lại bảng quy tắc điều trị.",
                    disease.getName(), friendlyGardenName(garden))
                    : String.format("Đã tổng hợp kế hoạch chăm sóc %s khi mắc bệnh %s. Hãy xác nhận trước khi áp dụng.",
                    friendlyGardenName(garden), disease.getName());

            return TreatmentPlanResult.builder()
                    .success(true)
                    .message(message)
                    .gardenId(garden.getId())
                    .gardenNickname(garden.getNickname())
                    .plantName(garden.getPlant() != null ? garden.getPlant().getCommonName() : null)
                    .diseaseName(disease.getName())
                    .currentSchedule(current)
                    .proposedActions(proposed)
                    .build();

        } catch (Exception ex) {
            return TreatmentPlanResult.builder()
                    .success(false)
                    .message("Lỗi hệ thống khi tạo kế hoạch: " + ex.getMessage())
                    .build();
        }
    }

public TreatmentPlanResult applyTreatmentPlan(Long userId,
                                              String plantName,
                                              String diseaseName,
                                              String gardenNickname,
                                              Long gardenId) {
    try {
        // 1. Validate input & tìm Garden + Disease
        ValidationResult validation = validateInputs(userId, plantName, diseaseName, gardenNickname, gardenId);
        if (!validation.success()) {
            return TreatmentPlanResult.builder()
                    .success(false)
                    .message(validation.message())
                    .build();
        }

        Garden garden = validation.garden();
        Disease disease = validation.disease();

        // 2. Kiểm tra bệnh đã gán cho cây chưa
        if (garden.getGardenDiseases() == null) {
            garden.setGardenDiseases(new ArrayList<>());
        }


        boolean alreadyHasDisease = garden.getGardenDiseases().stream()
                .anyMatch(gd -> Objects.equals(gd.getDisease().getId(), disease.getId())
                        && gd.getStatus() == DiseaseStatus.ACTIVE);

        if (!alreadyHasDisease) {

            GardenDisease newGD = GardenDisease.builder()
                    .garden(garden)
                    .disease(disease)
                    .detectedDate(LocalDateTime.now())
                    .status(DiseaseStatus.ACTIVE)
                    .build();

            garden.getGardenDiseases().add(newGD);

            gardenRepository.save(garden);
        }

        // 3. Áp dụng kế hoạch (applyChanges = true -> ghi vào DB)
        List<TreatmentPlanAction> actions = buildProposedActions(garden, disease, true);

        // 4. Tạo message chi tiết dựa trên actions
        String detailSummary = buildSummaryFromActions(
                garden,
                disease,
                actions,
                alreadyHasDisease
        );

        return TreatmentPlanResult.builder()
                .success(true)
                .message(detailSummary)
                .gardenId(garden.getId())
                .gardenNickname(garden.getNickname())
                .plantName(garden.getPlant() != null ? garden.getPlant().getCommonName() : null)
                .diseaseName(disease.getName())
                .proposedActions(actions)  // để Gemini có thêm data nếu cần
                .build();

    } catch (Exception ex) {
        ex.printStackTrace();
        return TreatmentPlanResult.builder()
                .success(false)
                .message("Lỗi hệ thống: " + ex.getMessage())
                .build();
    }
}
private String buildSummaryFromActions(Garden garden,
                                       Disease disease,
                                       List<TreatmentPlanAction> actions,
                                       boolean alreadyHasDisease) {
    StringBuilder sb = new StringBuilder();

    // câu mở đầu
    if (alreadyHasDisease) {
        sb.append("Bệnh **").append(disease.getName())
          .append("** đã được ghi nhận trước đó cho cây **")
          .append(friendlyGardenName(garden))
          .append("**. Tôi vừa cập nhật lại kế hoạch chăm sóc như sau:\n\n");
    } else {
        sb.append("Đã cập nhật cây **").append(friendlyGardenName(garden))
          .append("** là đang bị bệnh **").append(disease.getName())
          .append("** và điều chỉnh lịch chăm sóc như sau:\n\n");
    }

    if (actions == null || actions.isEmpty()) {
        sb.append("- Không có lịch nào cần điều chỉnh hoặc tạo mới dựa trên quy tắc điều trị hiện tại.");
        return sb.toString();
    }

    for (TreatmentPlanAction action : actions) {
        sb.append("• ").append(action.getDescription());
        if (action.getScheduledTime() != null) {
            sb.append(" (thời gian: ").append(action.getScheduledTime()).append(")");
        }
        sb.append("\n");

        if (action.getImpactedEvents() != null && !action.getImpactedEvents().isEmpty()) {
            sb.append("   ↳ Các lịch bị ảnh hưởng:\n");
            for (String impact : action.getImpactedEvents()) {
                sb.append("      - ").append(impact).append("\n");
            }
        }
        sb.append("\n");
    }

    return sb.toString().trim();
}


// Log tạo lịch mới
private String formatCreate(ScheduleType type, LocalDateTime time, String extraNote) {
    if (extraNote == null) extraNote = "";
    return String.format("[TẠO] %s tại %s%s",
            type.name(),
            formatTime(time),
            extraNote.isBlank() ? "" : (" - " + extraNote));
}


    private ValidationResult validateInputs(Long userId, String plantName, String diseaseName, String gardenNickname, Long gardenId) {
        if (userId == null) return ValidationResult.error("Thiếu thông tin người dùng.");
        if (plantName == null || plantName.isBlank()) return ValidationResult.error("Thiếu tên cây cần kiểm tra.");
        if (diseaseName == null || diseaseName.isBlank()) return ValidationResult.error("Thiếu tên bệnh cần xử lý.");

        Garden garden = resolveGarden(userId, plantName, gardenNickname, gardenId);
        if (garden == null) {
            return ValidationResult.error(String.format("Không tìm thấy cây '%s' trong vườn của bạn.", plantName));
        }

        Disease disease = resolveDisease(diseaseName);
        if (disease == null) {
            return ValidationResult.error(String.format("Không tìm thấy thông tin bệnh '%s'.", diseaseName));
        }

        return ValidationResult.success(garden, disease);
    }

private Garden resolveGarden(Long userId, String plantName, String gardenNickname, Long gardenId) {
        // 1. Ưu tiên cao nhất: Tìm theo ID (Chính xác tuyệt đối)
        if (gardenId != null && gardenId > 0) {
            Optional<Garden> byId = gardenRepository.findById(gardenId);
            if (byId.isPresent() && Objects.equals(byId.get().getUser().getId(), userId)) {
                return byId.get();
            }
        }

        List<Garden> gardens = gardenRepository.findByUserId(userId);
        if (gardens.isEmpty()) {
            return null;
        }

        // 2. Nếu Gemini trích xuất được tham số gardenNickname riêng biệt, dùng nó trước
        String normalizedNicknameParam = normalize(gardenNickname);
        if (!normalizedNicknameParam.isEmpty()) {
            for (Garden garden : gardens) {
                if (normalize(garden.getNickname()).equals(normalizedNicknameParam)) {
                    return garden;
                }
            }
        }

        // Chuẩn hóa tên người dùng nhập (ví dụ: "haha..." -> "haha")
        String normalizedInput = normalize(plantName); 

        // ------------------------------------------------------------------
        // 3. ƯU TIÊN CAO NHẤT KHI TÌM KIẾM TEXT: Tìm khớp chính xác NICKNAME
        // (Sửa theo yêu cầu: Tìm nickname trước vì nó unique)
        // ------------------------------------------------------------------
        for (Garden garden : gardens) {
            // Ví dụ: User nhập "haha...", cây có nickname "haha..." -> KHỚP NGAY
            if (normalize(garden.getNickname()).equals(normalizedInput)) {
                return garden; 
            }
        }

        // ------------------------------------------------------------------
        // 4. ƯU TIÊN TIẾP THEO: Tìm khớp chính xác COMMON NAME / SCIENTIFIC NAME
        // (Chỉ chạy vào đây nếu không tìm thấy nickname nào trùng khớp)
        // ------------------------------------------------------------------
        Garden commonNameMatch = null;
        for (Garden garden : gardens) {
            Plant plant = garden.getPlant();
            if (plant == null) continue;

            // Ví dụ: User nhập "Hoa sứ", nickname không có ai tên "Hoa sứ"
            // -> Tìm thấy cây có commonName là "Hoa sứ" -> Match
            if (normalize(plant.getCommonName()).equals(normalizedInput)
                    || normalize(plant.getScientificName()).equals(normalizedInput)) {
                
                // Lưu lại kết quả tìm thấy đầu tiên
                if (commonNameMatch == null) {
                    commonNameMatch = garden;
                }
                // LƯU Ý: Tại đây nếu muốn xử lý trường hợp có 2 cây Hoa sứ,
                // bạn có thể throw error hoặc return list để chatbot hỏi lại.
                // Hiện tại ta return cây đầu tiên tìm thấy.
                return garden; 
            }
        }

        // ------------------------------------------------------------------
        // 5. ƯU TIÊN CUỐI CÙNG: Tìm gần đúng (Chứa từ khóa)
        // (Tìm trong Nickname trước, rồi mới tìm trong Common Name)
        // ------------------------------------------------------------------
        
        // Tìm chứa trong Nickname (Ví dụ: Nhập "haha", nickname "haha hihi")
        for (Garden garden : gardens) {
            if (normalize(garden.getNickname()).contains(normalizedInput)) {
                return garden;
            }
        }

        // Tìm chứa trong Common Name (Ví dụ: Nhập "sứ", commonName "Hoa sứ")
        for (Garden garden : gardens) {
            Plant plant = garden.getPlant();
            if (plant != null && normalize(plant.getCommonName()).contains(normalizedInput)) {
                return garden;
            }
        }

        return null;
    }
    private Disease resolveDisease(String diseaseName) {
        List<Disease> matches = diseaseRepository.searchByName(diseaseName);
        if (matches == null || matches.isEmpty()) return null;

        String normalizedTarget = normalize(diseaseName);
        for (Disease disease : matches) {
            if (normalize(disease.getName()).equals(normalizedTarget)) {
                return disease;
            }
        }
        return matches.get(0);
    }

    private List<TreatmentPlanAction> mapCurrentSchedule(Garden garden) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(PREVIEW_DAYS);

        List<GardenSchedule> schedules = scheduleRepository.findSchedulesBetween(
                garden.getId(), now.minusDays(1), future
        );

        return schedules.stream()
                .sorted(Comparator.comparing(GardenSchedule::getScheduledTime))
                .map(schedule -> TreatmentPlanAction.builder()
                        .type(schedule.getType() != null ? schedule.getType().name() : ScheduleType.OTHER.name())
                        .scheduledTime(formatTime(schedule.getScheduledTime()))
                        .description(buildScheduleDescription(schedule))
                        .impactedEvents(Collections.emptyList())
                        .build())
                .collect(Collectors.toList());
    }

    private String buildScheduleDescription(GardenSchedule schedule) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ghi chú: ").append(schedule.getNote() != null ? schedule.getNote() : "(không có)");

        if (schedule.getType() == ScheduleType.WATERING && schedule.getWaterAmount() != null) {
            builder.append(" | Lượng nước: ").append(schedule.getWaterAmount()).append(" lít");
        }
        if (schedule.getType() == ScheduleType.FERTILIZING && schedule.getFertilityType() != null) {
            builder.append(" | Phân bón: ").append(schedule.getFertilityType());
        }
        if (schedule.getType() == ScheduleType.FUNGICIDE && schedule.getFungicideType() != null) {
            builder.append(" | Thuốc: ").append(schedule.getFungicideType());
        }
        builder.append(" | Trạng thái: ").append(schedule.getCompletion());
        return builder.toString();
    }

private List<TreatmentPlanAction> buildProposedActions(Garden garden, Disease disease, boolean applyChanges) {
    List<TreatmentPlanAction> actions = new ArrayList<>();

    LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0);
    int maxStopWateringDays = 0;
    Map<String, Integer> fungicideMap = new HashMap<>();
    List<String> pruningNotes = new ArrayList<>();
    List<TreatmentRule> otherRules = new ArrayList<>();

    // 1. Phân loại các rule điều trị
    if (disease.getTreatmentRules() != null) {
        for (TreatmentRule rule : disease.getTreatmentRules()) {
            if (rule.getType() == null) continue;
            switch (rule.getType()) {
                case STOP_WATERING -> 
                        maxStopWateringDays = Math.max(maxStopWateringDays, rule.getIntervalDays());
                case FUNGICIDE -> {
                    if (rule.getFungicideType() != null) {
                        fungicideMap.put(
                            rule.getFungicideType(),
                            fungicideMap.containsKey(rule.getFungicideType())
                                ? Math.min(fungicideMap.get(rule.getFungicideType()), rule.getIntervalDays())
                                : rule.getIntervalDays()
                        );
                    }
                }
                case PRUNNING -> {
                    if (rule.getDescription() != null) pruningNotes.add(rule.getDescription());
                }
                default -> otherRules.add(rule);
            }
        }
    }

    // 2. NGƯNG TƯỚI NHIỀU NGÀY
    if (maxStopWateringDays > 0) {
        LocalDateTime stopWaterEndTime = startTime.plusDays(maxStopWateringDays);

        // Tìm các lịch tưới nằm trong khoảng thời gian bị cấm
        List<GardenSchedule> wateringEvents = scheduleRepository
                .findByGardenAndTypeAndScheduledTimeBetween(
                        garden, ScheduleType.WATERING, startTime, stopWaterEndTime);

        List<String> impacted = new ArrayList<>();

        long delayDays = ChronoUnit.DAYS.between(startTime, stopWaterEndTime);
        if (delayDays <= 0) delayDays = 1;

        for (GardenSchedule water : wateringEvents) {
            LocalDateTime newTime = water.getScheduledTime()
                    .plusDays(delayDays)
                    .withHour(8)
                    .withMinute(0);

            impacted.add(formatReschedule(water.getScheduledTime(), newTime));
            if (applyChanges) {
                water.setScheduledTime(newTime);
                water.setNote(appendNote(
                        water.getNote(),
                        "Dời " + delayDays + " ngày do trị bệnh " + disease.getName()
                ));
                scheduleRepository.save(water);
            }
        }

        // ✅ Tạo N ngày STOP_WATERING trong DB + action chi tiết
        if (applyChanges) {
            for (int i = 0; i < maxStopWateringDays; i++) {
                LocalDateTime dayTime = startTime.plusDays(i).withHour(8).withMinute(0);

                GardenSchedule stop = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.STOP_WATERING)
                        .scheduledTime(dayTime)
                        .completion(Completion.NotDone)
                        .note(String.format(
                                "Ngày %d/%d ngưng tưới do bệnh %s",
                                i + 1, maxStopWateringDays, disease.getName()))
                        .build();
                scheduleRepository.save(stop);
            }
        }

        // 1 action tóm tắt cả giai đoạn + list lịch bị dời
        actions.add(TreatmentPlanAction.builder()
                .type(ScheduleType.STOP_WATERING.name())
                .scheduledTime(formatTime(startTime))
                .description(String.format(
                        "Ngưng tưới %s trong %d ngày, từ %s đến %s.",
                        friendlyGardenName(garden),
                        maxStopWateringDays,
                        formatTime(startTime),
                        formatTime(stopWaterEndTime.minusSeconds(1))
                ))
                .impactedEvents(impacted)
                .build());
    }

    // 3. PHUN THUỐC NHIỀU LẦN (chu kỳ intervalDays, trong PREVIEW_DAYS)
    for (Map.Entry<String, Integer> entry : fungicideMap.entrySet()) {
        String fungicideType = entry.getKey();
        int intervalDays = entry.getValue();

        GardenSchedule lastFungicide = scheduleRepository
                .findTopByGardenAndTypeAndFungicideTypeOrderByScheduledTimeDesc(
                        garden, ScheduleType.FUNGICIDE, fungicideType)
                .orElse(null);

        LocalDateTime nextScheduleTime = startTime;
        if (lastFungicide != null) {
            LocalDateTime nextAllowedTime =
                    lastFungicide.getScheduledTime().plusDays(intervalDays);
            if (nextAllowedTime.isAfter(LocalDateTime.now())) {
                nextScheduleTime = nextAllowedTime.withHour(8).withMinute(0);
            }
        }

        // Khoảng “cấm bón phân” cho lần phun đầu
        LocalDateTime firstFungicideEnd = nextScheduleTime.plusDays(intervalDays);

        List<GardenSchedule> fertilizingEvents = scheduleRepository
                .findByGardenAndTypeAndScheduledTimeBetween(
                        garden,
                        ScheduleType.FERTILIZING,
                        nextScheduleTime,
                        firstFungicideEnd
                );

        List<String> impacted = new ArrayList<>();
        long delayFertilize = intervalDays;

        for (GardenSchedule fertilize : fertilizingEvents) {
            LocalDateTime newTime = fertilize.getScheduledTime()
                    .plusDays(delayFertilize)
                    .withHour(8)
                    .withMinute(0);
            impacted.add(formatReschedule(fertilize.getScheduledTime(), newTime));
            if (applyChanges) {
                fertilize.setScheduledTime(newTime);
                fertilize.setNote(appendNote(
                        fertilize.getNote(),
                        "Dời do phun thuốc " + fungicideType));
                scheduleRepository.save(fertilize);
            }
        }

        // ✅ Tạo nhiều lịch phun trong PREVIEW_DAYS
        List<String> createdTimes = new ArrayList<>();
        LocalDateTime horizon = LocalDateTime.now().plusDays(PREVIEW_DAYS);

        if (applyChanges) {
            LocalDateTime current = nextScheduleTime;
            while (!current.isAfter(horizon)) {
                GardenSchedule fungicide = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.FUNGICIDE)
                        .fungicideType(fungicideType)
                        .scheduledTime(current)
                        .completion(Completion.NotDone)
                        .note("Phun " + fungicideType + " (chu kỳ " + intervalDays
                                + " ngày) trị " + disease.getName())
                        .build();
                scheduleRepository.save(fungicide);

                createdTimes.add(formatTime(current));
                current = current.plusDays(intervalDays);
            }
        } else {
            // preview mode: chỉ show text, không lưu DB
            LocalDateTime current = nextScheduleTime;
            while (!current.isAfter(LocalDateTime.now().plusDays(PREVIEW_DAYS))) {
                createdTimes.add(formatTime(current));
                current = current.plusDays(intervalDays);
            }
        }

        actions.add(TreatmentPlanAction.builder()
                .type(ScheduleType.FUNGICIDE.name())
                .scheduledTime(formatTime(nextScheduleTime))
                .description(String.format(
                        "Phun %s theo chu kỳ %d ngày. Các lịch phun sắp tới: %s",
                        fungicideType,
                        intervalDays,
                        String.join(", ", createdTimes)
                ))
                .impactedEvents(impacted)
                .build());
    }

    // 4. Xử lý CẮT TỈA (PRUNING)
    if (!pruningNotes.isEmpty()) {
        List<String> impacted = new ArrayList<>();

        if (applyChanges) {
            GardenSchedule pruning = GardenSchedule.builder()
                    .garden(garden)
                    .type(ScheduleType.PRUNNING)
                    .scheduledTime(startTime)
                    .completion(Completion.NotDone)
                    .note(String.join("; ", pruningNotes))
                    .build();
            scheduleRepository.save(pruning);

            impacted.add(formatCreate(
                    ScheduleType.PRUNNING,
                    startTime,
                    String.join("; ", pruningNotes)));
        }

        actions.add(TreatmentPlanAction.builder()
                .type(ScheduleType.PRUNNING.name())
                .scheduledTime(formatTime(startTime))
                .description(String.format("Tỉa cành/lá: %s", String.join("; ", pruningNotes)))
                .impactedEvents(impacted)
                .build());
    }

        // 5. Các rule khác
        for (TreatmentRule rule : otherRules) {
            String note = rule.getDescription() != null ? rule.getDescription() : "Tuân thủ hướng dẫn chăm sóc chung.";
            actions.add(TreatmentPlanAction.builder()
                    .type(rule.getType() != null ? rule.getType().name() : ScheduleType.OTHER.name())
                    .scheduledTime(formatTime(startTime))
                    .description(note)
                    .impactedEvents(Collections.emptyList())
                    .build());
        }

        return actions;
    }

    private String appendNote(String existing, String suffix) {
        if (existing == null || existing.isBlank()) return suffix;
        if (existing.contains(suffix)) return existing;
        return existing + " | " + suffix;
    }

    private String formatReschedule(LocalDateTime oldTime, LocalDateTime newTime) {
        return String.format("%s → %s", formatTime(oldTime), formatTime(newTime));
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? ISO_FORMATTER.format(time) : null;
    }

    private String friendlyGardenName(Garden garden) {
        if (garden.getNickname() != null && !garden.getNickname().isBlank()) {
            return garden.getNickname();
        }
        if (garden.getPlant() != null && garden.getPlant().getCommonName() != null) {
            return garden.getPlant().getCommonName();
        }
        return "cây trong vườn";
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record ValidationResult(boolean success, String message, Garden garden, Disease disease) {
        static ValidationResult success(Garden garden, Disease disease) {
            return new ValidationResult(true, null, garden, disease);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message, null, null);
        }
    }
}
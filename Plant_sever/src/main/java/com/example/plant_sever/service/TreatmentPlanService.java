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
            // 1. Kim tra thng tin ngi dng v tn cy (Bt buc phi c tn cy)
            if (userId == null) {
                return TreatmentPlanResult.builder().success(false).message("Thiu thng tin ngi dng.").build();
            }
            if (plantName == null || plantName.isBlank()) {
                return TreatmentPlanResult.builder().success(false).message("Vui lng cung cp tn cy hoc bit danh cy trong vn.").build();
            }

            // 2. Tm kim Cy trong vn (u tin Nickname -> Common Name)
            Garden garden = resolveGarden(userId, plantName, gardenNickname, gardenId);
            
            // Tu i bnh ACTIVE ca cy khi ngi dng cha cung cp tn bnh
            if ((diseaseName == null || diseaseName.isBlank()) && garden != null) {
                Disease activeDisease = pickActiveDisease(garden);
                if (activeDisease != null) {
                    diseaseName = activeDisease.getName();
                }
            }
            
            // Trng hp KHNG tm thy cy -> Bo li ngay
            if (garden == null) {
                return TreatmentPlanResult.builder()
                        .success(false)
                        .message("Khng tm thy cy no tn l '" + plantName + "' trong vn ca bn. Bn  thm cy ny vo ng dng cha?")
                        .build();
            }

            // 3. X l trng hp THIU TN BNH (y l ch sa li "haha...")
            // Nu tm thy cy nhng ngi dng cha ni bnh -> Tr v Success=True  Gemini bit l c cy
            // Km theo cu hi gi   Gemini hi li ngi dng.
            if (diseaseName == null || diseaseName.isBlank()) {
                return TreatmentPlanResult.builder()
                        .success(true) 
                        .message("Da tim thay cay **" + friendlyGardenName(garden) + "** (ID: " + garden.getId() + ").\n"
                                + "Cay nay dang bi benh gi? Hay mo ta trieu chung de toi lap ke hoach xu ly.")
                        .gardenId(garden.getId())
                        .gardenNickname(garden.getNickname())
                        .plantName(garden.getPlant() != null ? garden.getPlant().getCommonName() : null)
                        .build();
            }

            // 4. Nu c tn bnh -> Tm thng tin bnh trong DB
            Disease disease = resolveDisease(diseaseName);
            if (disease == null) {
                return TreatmentPlanResult.builder()
                        .success(false)
                        .message("Tm thy cy **" + friendlyGardenName(garden) + "** nhng h thng cha nhn din c bnh '" + diseaseName + "'. Vui lng m t khc i (v d: m l, thi r...).")
                        .gardenId(garden.getId())
                        .build();
            }

            // 5. C  Cy v Bnh -> Tnh ton lch trnh (Logic c)
            List<TreatmentPlanAction> current = mapCurrentSchedule(garden);
            List<TreatmentPlanAction> proposed = buildProposedActions(garden, disease, false);

            String message = proposed.isEmpty()
                    ? String.format("Khong tim thay huong xu ly cu the cho benh %s cua %s. Hay kiem tra lai bang quy tac dieu tri.",
                    disease.getName(), friendlyGardenName(garden))
                    : String.format("Da tong hop ke hoach cham soc %s khi mac benh %s. Hay xac nhan truoc khi ap dung.",
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
                    .message("Li h thng khi to k hoch: " + ex.getMessage())
                    .build();
        }
    }

    public TreatmentPlanResult applyTreatmentPlan(Long userId,
                                                  String plantName,
                                                  String diseaseName,
                                                  String gardenNickname,
                                                  Long gardenId) {
        try {
            // 1. Validate input & tm Garden + Disease
            ValidationResult validation = validateInputs(userId, plantName, diseaseName, gardenNickname, gardenId);
            if (!validation.success()) {
                return TreatmentPlanResult.builder()
                        .success(false)
                        .message(validation.message())
                        .build();
            }

            Garden garden = validation.garden();
            Disease disease = validation.disease();

            // 2. Kim tra bnh  gn cho cy cha
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

            // 3. p dng k hoch (applyChanges = true -> ghi vo DB)
            List<TreatmentPlanAction> actions = buildProposedActions(garden, disease, true);

            // 4. To message chi tit da trn actions
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
                    .proposedActions(actions)
                    .build();

        } catch (Exception ex) {
            ex.printStackTrace();
            return TreatmentPlanResult.builder()
                    .success(false)
                    .message("Li h thng: " + ex.getMessage())
                    .build();
        }
    }
    private String buildSummaryFromActions(Garden garden,
                                           Disease disease,
                                           List<TreatmentPlanAction> actions,
                                           boolean alreadyHasDisease) {
        StringBuilder sb = new StringBuilder();

        // cu mo u
        if (alreadyHasDisease) {
            sb.append("Bnh **").append(disease.getName())
              .append("**  c ghi nhn trc  cho cy **")
              .append(friendlyGardenName(garden))
              .append("**. Ti va cp nht li k hoch chm sc nh sau:\n\n");
        } else {
            sb.append(" cp nht cy **").append(friendlyGardenName(garden))
              .append("** l ang b bnh **").append(disease.getName())
              .append("** v iu chnh lch chm sc nh sau:\n\n");
        }

        if (actions == null || actions.isEmpty()) {
            sb.append("- Khng c lch no cn iu chnh hoc to mi da trn quy tc iu tr hin ti.");
            return sb.toString();
        }

        for (TreatmentPlanAction action : actions) {
            sb.append(" ").append(action.getDescription());
            if (action.getScheduledTime() != null) {
                sb.append(" (thi gian: ").append(action.getScheduledTime()).append(")");
            }
            sb.append("\n");

            if (action.getImpactedEvents() != null && !action.getImpactedEvents().isEmpty()) {
                sb.append("    Cc lch b nh hng:\n");
                for (String impact : action.getImpactedEvents()) {
                    sb.append("      - ").append(impact).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }


// Log to lch mi
private String formatCreate(ScheduleType type, LocalDateTime time, String extraNote) {
    if (extraNote == null) extraNote = "";
    return String.format("[TO] %s ti %s%s",
            type.name(),
            formatTime(time),
            extraNote.isBlank() ? "" : (" - " + extraNote));
}


    private ValidationResult validateInputs(Long userId, String plantName, String diseaseName, String gardenNickname, Long gardenId) {
        if (userId == null) return ValidationResult.error("Thiu thng tin ngi dng.");
        if (plantName == null || plantName.isBlank()) return ValidationResult.error("Thiu tn cy cn kim tra.");

        Garden garden = resolveGarden(userId, plantName, gardenNickname, gardenId);
        if (garden == null) {
            return ValidationResult.error(String.format("Khng tm thy cy '%s' trong vn ca bn.", plantName));
        }

        if (diseaseName == null || diseaseName.isBlank()) {
            Disease activeDisease = pickActiveDisease(garden);
            if (activeDisease != null) {
                diseaseName = activeDisease.getName();
            }
        }

        if (diseaseName == null || diseaseName.isBlank()) {
            return ValidationResult.error("Thiu tn bnh cn x l ho c bnh ang ACTIVE cho cy.");
        }

        Disease disease = resolveDisease(diseaseName);
        if (disease == null) {
            return ValidationResult.error(String.format("Khng tm thy thng tin bnh '%s'.", diseaseName));
        }

        return ValidationResult.success(garden, disease);
    }

private Garden resolveGarden(Long userId, String plantName, String gardenNickname, Long gardenId) {
        // 1. u tin cao nht: Tm theo ID (Chnh xc tuyt i)
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

        // 2. Nu Gemini trch xut c tham s gardenNickname ring bit, dng n trc
        String normalizedNicknameParam = normalize(gardenNickname);
        if (!normalizedNicknameParam.isEmpty()) {
            for (Garden garden : gardens) {
                if (normalize(garden.getNickname()).equals(normalizedNicknameParam)) {
                    return garden;
                }
            }
        }

        // Chun ha tn ngi dng nhp (v d: "haha..." -> "haha")
        String normalizedInput = normalize(plantName); 

        // ------------------------------------------------------------------
        // 3. U TIN CAO NHT KHI TM KIM TEXT: Tm khp chnh xc NICKNAME
        // (Sa theo yu cu: Tm nickname trc v n unique)
        // ------------------------------------------------------------------
        for (Garden garden : gardens) {
            // V d: User nhp "haha...", cy c nickname "haha..." -> KHP NGAY
            if (normalize(garden.getNickname()).equals(normalizedInput)) {
                return garden; 
            }
        }

        // ------------------------------------------------------------------
        // 4. U TIN TIP THEO: Tm khp chnh xc COMMON NAME / SCIENTIFIC NAME
        // (Ch chy vo y nu khng tm thy nickname no trng khp)
        // ------------------------------------------------------------------
        Garden commonNameMatch = null;
        for (Garden garden : gardens) {
            Plant plant = garden.getPlant();
            if (plant == null) continue;

            // V d: User nhp "Hoa s", nickname khng c ai tn "Hoa s"
            // -> Tm thy cy c commonName l "Hoa s" -> Match
            if (normalize(plant.getCommonName()).equals(normalizedInput)
                    || normalize(plant.getScientificName()).equals(normalizedInput)) {
                
                // Lu li kt qu tm thy u tin
                if (commonNameMatch == null) {
                    commonNameMatch = garden;
                }
                // LU : Ti y nu mun x l trng hp c 2 cy Hoa s,
                // bn c th throw error hoc return list  chatbot hi li.
                // Hin ti ta return cy u tin tm thy.
                return garden; 
            }
        }

        // ------------------------------------------------------------------
        // 5. U TIN CUI CNG: Tm gn ng (Cha t kha)
        // (Tm trong Nickname trc, ri mi tm trong Common Name)
        // ------------------------------------------------------------------
        
        // Tm cha trong Nickname (V d: Nhp "haha", nickname "haha hihi")
        for (Garden garden : gardens) {
            if (normalize(garden.getNickname()).contains(normalizedInput)) {
                return garden;
            }
        }

        // Tm cha trong Common Name (V d: Nhp "s", commonName "Hoa s")
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

    private Disease pickActiveDisease(Garden garden) {
        if (garden == null || garden.getGardenDiseases() == null) return null;

        return garden.getGardenDiseases().stream()
                .filter(gd -> gd.getStatus() == DiseaseStatus.ACTIVE && gd.getDisease() != null)
                .sorted(
                        Comparator.comparing(
                                        (GardenDisease gd) -> Optional.ofNullable(gd.getDetectedDate()).orElse(LocalDateTime.MIN))
                                .reversed()
                                .thenComparing(gd -> gd.getDisease().getPriority(), Comparator.reverseOrder())
                )
                .map(GardenDisease::getDisease)
                .findFirst()
                .orElse(null);
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
        builder.append("Ghi ch: ").append(schedule.getNote() != null ? schedule.getNote() : "(khng c)");

        if (schedule.getType() == ScheduleType.WATERING && schedule.getWaterAmount() != null) {
            builder.append(" | Lng nc: ").append(schedule.getWaterAmount()).append(" lt");
        }
        if (schedule.getType() == ScheduleType.FERTILIZING && schedule.getFertilityType() != null) {
            builder.append(" | Phn bn: ").append(schedule.getFertilityType());
        }
        if (schedule.getType() == ScheduleType.FUNGICIDE && schedule.getFungicideType() != null) {
            builder.append(" | Thuc: ").append(schedule.getFungicideType());
        }
        builder.append(" | Trng thi: ").append(schedule.getCompletion());
        return builder.toString();
    }

private List<TreatmentPlanAction> buildProposedActions(Garden garden, Disease disease, boolean applyChanges) {
    List<TreatmentPlanAction> actions = new ArrayList<>();

    LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0);
    int maxStopWateringDays = 0;
    Map<String, Integer> fungicideMap = new HashMap<>();
    List<String> pruningNotes = new ArrayList<>();
    List<TreatmentRule> otherRules = new ArrayList<>();

    // 1. Phn loi cc rule iu tr
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

    // 2. NGNG TI NHIU NGY
    if (maxStopWateringDays > 0) {
        LocalDateTime stopWaterEndTime = startTime.plusDays(maxStopWateringDays);

        // Tm cc lch ti nm trong khong thi gian b cm
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
                        "Di " + delayDays + " ngy do tr bnh " + disease.getName()
                ));
                scheduleRepository.save(water);
            }
        }

        //  To N ngy STOP_WATERING trong DB + action chi tit
        if (applyChanges) {
            for (int i = 0; i < maxStopWateringDays; i++) {
                LocalDateTime dayTime = startTime.plusDays(i).withHour(8).withMinute(0);

                GardenSchedule stop = GardenSchedule.builder()
                        .garden(garden)
                        .type(ScheduleType.STOP_WATERING)
                        .scheduledTime(dayTime)
                        .completion(Completion.NotDone)
                        .note(String.format(
                                "Ngy %d/%d ngng ti do bnh %s",
                                i + 1, maxStopWateringDays, disease.getName()))
                        .build();
                scheduleRepository.save(stop);
            }
        }

        // 1 action tm tt c giai on + list lch b di
        actions.add(TreatmentPlanAction.builder()
                .type(ScheduleType.STOP_WATERING.name())
                .scheduledTime(formatTime(startTime))
                .description(String.format(
                        "Ngng ti %s trong %d ngy, t %s n %s.",
                        friendlyGardenName(garden),
                        maxStopWateringDays,
                        formatTime(startTime),
                        formatTime(stopWaterEndTime.minusSeconds(1))
                ))
                .impactedEvents(impacted)
                .build());
    }

    // 3. PHUN THUC NHIU LN (chu k intervalDays, trong PREVIEW_DAYS)
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

        // Khong cm bn phn cho ln phun u
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
                        "Di do phun thuc " + fungicideType));
                scheduleRepository.save(fertilize);
            }
        }

        //  To nhiu lch phun trong PREVIEW_DAYS
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
                        .note("Phun " + fungicideType + " (chu k " + intervalDays
                                + " ngy) tr " + disease.getName())
                        .build();
                scheduleRepository.save(fungicide);

                createdTimes.add(formatTime(current));
                current = current.plusDays(intervalDays);
            }
        } else {
            // preview mode: ch show text, khng lu DB
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
                        "Phun %s theo chu k %d ngy. Cc lch phun sp ti: %s",
                        fungicideType,
                        intervalDays,
                        String.join(", ", createdTimes)
                ))
                .impactedEvents(impacted)
                .build());
    }

    // 4. X l CT TA (PRUNING)
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
                .description(String.format("Ta cnh/l: %s", String.join("; ", pruningNotes)))
                .impactedEvents(impacted)
                .build());
    }

        // 5. Cc rule khc
        for (TreatmentRule rule : otherRules) {
            String note = rule.getDescription() != null ? rule.getDescription() : "Tun th hng dn chm sc chung.";
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
        return String.format("%s  %s", formatTime(oldTime), formatTime(newTime));
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
        return "cy trong vn";
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


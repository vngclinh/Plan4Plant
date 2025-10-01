package com.example.plant_sever.DTO;

import com.example.plant_sever.model.Completion;
import com.example.plant_sever.model.ScheduleType;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GardenScheduleRequest {
    private Long gardenId;
    private ScheduleType type;
    private LocalDateTime scheduledTime;
    private Completion completion;   // optional, defaults to NotDone
    private String note;
    private Double waterAmount;      // optional, default to plant.waterAmount()
}


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
public class GardenScheduleResponse {
    private Long id;
    private Long gardenId;
    private ScheduleType type;
    private LocalDateTime scheduledTime;
    private Completion completion;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


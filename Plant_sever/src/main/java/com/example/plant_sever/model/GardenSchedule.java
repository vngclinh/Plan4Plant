package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GardenSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id", nullable = false)
    private Garden garden;

    @Enumerated(EnumType.STRING)
    private ScheduleType type;

    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    private Completion completion = Completion.NotDone;

    @Column(length = 500)
    private String note;


    private Double waterAmount;
    private Double fertilityAmount;
    private String fertilityType;
    private String fungicideType;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treatment_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disease_id", nullable = false)
    private Disease disease;

    @Enumerated(EnumType.STRING)
    private ScheduleType type;

    private int intervalDays;

    private String fungicideType;

    @Column(length = 500)
    private String description;
}
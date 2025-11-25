package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "garden_disease")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class GardenDisease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "garden_id")
    private Garden garden;

    @ManyToOne
    @JoinColumn(name = "disease_id")
    private Disease disease;

    private LocalDateTime detectedDate;
    private LocalDateTime curedDate;

    @Enumerated(EnumType.STRING)
    private DiseaseStatus status;
}
package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "garden")
public class Garden {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "plant_id")
    private Plant plant;

    private String nickname;

    private LocalDateTime dateAdded;

    @OneToMany(mappedBy = "garden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Diary> diaries = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private GardenType type;

    @Enumerated(EnumType.STRING)
    private GardenStatus status = GardenStatus.ALIVE;

    @OneToMany(mappedBy = "garden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GardenImage> images = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private PotType potType;

    @ManyToMany
    @JoinTable(
            name = "garden_disease",
            joinColumns = @JoinColumn(name = "garden_id"),
            inverseJoinColumns = @JoinColumn(name = "disease_id")
    )
    private List<Disease> diseases = new ArrayList<>();
    @OneToMany(mappedBy = "garden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GardenSchedule> schedules = new ArrayList<>();
}
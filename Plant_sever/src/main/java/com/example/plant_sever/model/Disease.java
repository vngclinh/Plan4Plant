package com.example.plant_sever.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "disease")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    private String scientificName;
    private String description;
    private String symptoms;
    private String causes;
    private String careguide;
    private int priority; // higher = more urgent treatment

    private String imageUrl;

@ManyToMany(mappedBy = "diseases")
@JsonIgnore // ❗ Thay vì @JsonBackReference
private List<Plant> plants = new ArrayList<>();

    @OneToMany(mappedBy = "disease", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonBackReference
    private List<GardenDisease> gardenDiseases = new ArrayList<>();


    @OneToMany(mappedBy = "disease", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("disease-treatment")
    private List<TreatmentRule> treatmentRules = new ArrayList<>();
}
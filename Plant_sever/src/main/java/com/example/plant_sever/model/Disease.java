package com.example.plant_sever.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disease")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @ManyToMany(mappedBy = "diseases")
    @JsonBackReference
    private List<Plant> plants = new ArrayList<>();
}
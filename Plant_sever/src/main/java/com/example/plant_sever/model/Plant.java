package com.example.plant_sever.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;


@Entity
@Getter
@Setter
@Table(name = "plant")
public class Plant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_name", length = 100, nullable = false)
    private String commonName;

    @Column(name = "scientific_name", length = 150)
    private String scientificName;

    @Column(name = "phylum", length = 100)
    private String phylum;

    @Column(name = "class", length = 100)
    private String plantClass;   // renamed, since `class` is reserved in Java

    @Column(name = "\"order\"", length = 100)
    private String plantOrder;   // renamed, since `order` is reserved in SQL

    @Column(name = "family", length = 100)
    private String family;

    @Column(name = "genus", length = 100)
    private String genus;

    @Column(name = "species", length = 100)
    private String species;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "water_schedule", length = 50)
    private String waterSchedule;

    @Column(name = "light", length = 50)
    private String light;

    @Column(name = "temperature", length = 50)
    private String temperature;

    @Column(columnDefinition = "TEXT")
    private String careguide;
    
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    private Integer minInterval;
    private Integer maxInterval;

    private Double minTemperature;
    private Double maxTemperature;

    private Double waterAmount;
}
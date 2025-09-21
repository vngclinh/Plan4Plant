package com.example.plant_sever.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "plant")
public class Plant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_name", nullable = false, columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String commonName;

    @Column(name = "scientific_name", columnDefinition = "VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String scientificName;

    @Column(name = "other_names", columnDefinition = "VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String otherNames;

    @Column(columnDefinition = "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Column(columnDefinition = "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String careguide;

    private String imagePath;

}

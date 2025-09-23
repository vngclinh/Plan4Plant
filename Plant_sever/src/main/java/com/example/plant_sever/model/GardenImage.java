package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="garden_image")
@Getter
@Setter
public class GardenImage {
    @Id @GeneratedValue
    private Long id;

    private String imageUrl;

    private LocalDateTime dateUploaded;

    @ManyToOne
    @JoinColumn(name = "garden_id")
    private Garden garden;
}
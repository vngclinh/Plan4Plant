package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "weather_data")

public class WeatherData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String regionKey;

    private LocalDate date;
    private double precipitationMm;
    private double temperatureMax;
    private double temperatureMin;
}
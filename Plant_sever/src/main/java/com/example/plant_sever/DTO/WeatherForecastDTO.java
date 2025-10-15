package com.example.plant_sever.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WeatherForecastDTO {
    private LocalDate date;
    private Double  precipitation;
    private Double maxTemperature; // maximum temperature of the day
    private Double minTemperature; // optional, if needed

    // Convenience constructor for only date and precipitation
    public WeatherForecastDTO(LocalDate date, double precipitation) {
        this.date = date;
        this.precipitation = precipitation;
    }
}



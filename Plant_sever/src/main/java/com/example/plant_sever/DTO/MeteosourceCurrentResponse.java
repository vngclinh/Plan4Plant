package com.example.plant_sever.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeteosourceCurrentResponse {
    private Current current;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Current {
        private Double temperature;           // °C
        private Double temperature_min;       // sometimes included
        private Double temperature_max;       // sometimes included
        private Double wind_speed;            // m/s
        private Double humidity;              // %
        private Precipitation precipitation;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Precipitation {
        private Double total;   // mm
        private String type;    // "none", "rain", etc.
    }
}

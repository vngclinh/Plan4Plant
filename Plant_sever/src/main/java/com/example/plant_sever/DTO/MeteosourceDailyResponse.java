package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class MeteosourceDailyResponse {
    private Daily daily;

    @Getter
    @Setter
    public static class Daily {
        private List<Day> data;
    }

    @Getter
    @Setter
    public static class Day {
        private String day;        // "2025-04-29"
        private AllDay all_day;
    }

    @Getter
    @Setter
    public static class AllDay {
        private double temperature;
        private double temperature_min;
        private double temperature_max;
        private Precipitation precipitation;
    }

    @Getter
    @Setter
    public static class Precipitation {
        private double total;      // lượng mưa mm
        private String type;       // "none", "rain", etc.
    }
}

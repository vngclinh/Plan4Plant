package com.example.plant_sever.service;

import com.example.plant_sever.DTO.MeteosourceDailyResponse;
import com.example.plant_sever.DTO.WeatherForecastDTO;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeteosourceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${meteosource.api.key}")
    private String apiKey;

    @Value("${meteosource.api.url:https://www.meteosource.com/api/v1/free/point}")
    private String baseUrl;

    /**
     * Gọi Meteosource API để lấy 7-day forecast
     */
    public List<WeatherForecastDTO> get7DayForecast(double lat, double lon) {
        String url = String.format(
                Locale.US,
                "%s?lat=%.4f&lon=%.4f&sections=daily&key=%s",
                baseUrl, lat, lon, apiKey
        );

        MeteosourceDailyResponse response = restTemplate.getForObject(url, MeteosourceDailyResponse.class);

        if (response == null || response.getDaily() == null || response.getDaily().getData() == null) {
            return Collections.emptyList();
        }

        return response.getDaily().getData().stream()
                .limit(7)
                .map(d -> new WeatherForecastDTO(
                        LocalDate.parse(d.getDay()),
                        d.getAll_day().getPrecipitation().getTotal()
                ))
                .collect(Collectors.toList());
    }
}

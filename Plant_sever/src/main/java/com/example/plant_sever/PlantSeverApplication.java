package com.example.plant_sever;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlantSeverApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlantSeverApplication.class, args);
    }

}

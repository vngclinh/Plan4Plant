package com.example.plant_sever.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "diary")
@Getter
@Setter
public class Diary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate entryTime;

    @Column(columnDefinition = "TEXT") 
    private String content;

    @ManyToOne
    @JoinColumn(name = "garden_id", nullable = false)
    private Garden garden;
}

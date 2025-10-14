package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
    private String fullname;
    private String username;
    private String password;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private Double lat;
    private Double lon;
}
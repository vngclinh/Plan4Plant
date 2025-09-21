package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    } // default constructor for JSON parsing

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}

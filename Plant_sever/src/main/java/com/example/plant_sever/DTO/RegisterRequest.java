package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String phoneNumber;
    private String fullname;

    public RegisterRequest() {
    }

    public RegisterRequest(String username, String password, String email, String phoneNumber, String fullname) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.fullname = fullname;
    }
}

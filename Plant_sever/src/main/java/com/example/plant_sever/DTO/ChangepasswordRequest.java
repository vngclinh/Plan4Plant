package com.example.plant_sever.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangepasswordRequest {
    private String oldPassword;
    private String newPassword;
    public ChangepasswordRequest() {}
    public ChangepasswordRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }
}

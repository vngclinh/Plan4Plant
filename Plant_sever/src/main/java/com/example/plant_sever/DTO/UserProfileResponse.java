package com.example.plant_sever.DTO;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullname;
    private String email;
    private String phone;
    private String avatarUrl;
}

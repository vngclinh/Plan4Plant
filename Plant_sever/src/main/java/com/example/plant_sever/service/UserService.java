package com.example.plant_sever.service;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ChangepasswordRequest;
import com.example.plant_sever.DTO.UpdateUserRequest;
import com.example.plant_sever.Security.JwtUtils;
import com.example.plant_sever.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public void changePassword(ChangepasswordRequest request, String token) {
        // extract username from JWT
        String username = jwtUtils.getUsernameFromJwt(token);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // check old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        // update new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
    }

    @Transactional
    public User updateUser(String username, UpdateUserRequest request) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullname() != null) user.setFullname(request.getFullname());
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getPassword() != null) user.setPassword(request.getPassword());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getLat() != null) user.setLat(request.getLat());
        if (request.getLon() != null) user.setLon(request.getLon());

        return userRepo.save(user);
    }
}
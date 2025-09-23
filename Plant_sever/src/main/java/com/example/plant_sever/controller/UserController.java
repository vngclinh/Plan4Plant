package com.example.plant_sever.controller;

import com.example.plant_sever.DTO.ChangepasswordRequest;
import com.example.plant_sever.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangepasswordRequest request,
            @RequestHeader("Authorization") String token) {

        // remove "Bearer " if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        userService.changePassword(request, token);
        return ResponseEntity.ok("Password changed successfully");
    }
}

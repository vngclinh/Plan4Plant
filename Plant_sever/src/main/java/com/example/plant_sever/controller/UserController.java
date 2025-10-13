package com.example.plant_sever.controller;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ChangepasswordRequest;
import com.example.plant_sever.DTO.UserProfileResponse;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.CloudinaryService;
import com.example.plant_sever.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserRepo userRepository;
    private final CloudinaryService cloudinaryService;
    private final UserRepo userRepo;

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangepasswordRequest request,
            @RequestHeader("Authorization") String token) {

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        userService.changePassword(request, token);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        if (file.getSize() > 2 * 1024 * 1024) { // 2 MB
            return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds 2MB"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Only images allowed"));
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String avatarUrl = cloudinaryService.uploadUserAvatar(user.getId(), file);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Avatar uploaded successfully",
                "avatarUrl", avatarUrl
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> user = userRepo.findByUsername(username);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.get().getId())
                .username(user.get().getUsername())
                .fullname(user.get().getFullname())
                .email(user.get().getEmail())
                .phone(user.get().getPhoneNumber())
                .avatarUrl(user.get().getAvatarUrl())
                .build();

        return ResponseEntity.ok(response);
    }


}

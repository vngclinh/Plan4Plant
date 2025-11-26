package com.example.plant_sever.controller;

import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.JwtResponse;
import com.example.plant_sever.DTO.LoginRequest;
import com.example.plant_sever.DTO.RegisterRequest;
import com.example.plant_sever.Security.JwtUtils;
import com.example.plant_sever.model.Level;
import com.example.plant_sever.model.User;
import com.example.plant_sever.service.EmailService;
import com.example.plant_sever.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    // Temporary store for reset codes (better: Redis or DB)
    private final Map<String, ResetCodeEntry> resetCodes = new HashMap<>();

    public AuthController(UserRepo userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtUtils jwtUtils,
                          RefreshTokenService refreshTokenService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
    }

    // ======================= AUTH =========================

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
        if (userRepo.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }
        if (userRepo.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setFullname(request.getFullname());
        user.setLevel(Level.MAM);
        user.setStreak(0);
        userRepo.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepo.findByUsername(request.getUsername());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Invalid username or password");
        }

        User user = userOpt.get();
        String jwt = jwtUtils.generateJwt(user.getUsername());
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername());

        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken, user.getUsername()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        String username = refreshTokenService.validateAndGetUsername(token);

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Invalid or expired refresh token");
        }

        String newJwt = jwtUtils.generateJwt(username);
        return ResponseEntity.ok(new JwtResponse(newJwt, token, username));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        refreshTokenService.revokeToken(token);
        return ResponseEntity.ok("User logged out successfully!");
    }

    // ======================= FORGOT PASSWORD =========================

    /** Step 1: Request reset code **/
    @Autowired
    private EmailService emailService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Email not found");
        }

        // Generate 6-digit code
        String code = String.format("%06d", new Random().nextInt(999999));

        // Save with expiration (15 minutes)
        resetCodes.put(email, new ResetCodeEntry(code, Instant.now().plusSeconds(900)));

        // Send email
        String subject = "Password Reset Verification Code";
        String text = "Your verification code is: " + code + "\n\nThis code will expire in 15 minutes.";
        emailService.sendEmail(email, subject, text);

        return ResponseEntity.ok("Verification code sent to your email.");
    }

    /** Step 2: Verify code **/
    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        ResetCodeEntry entry = resetCodes.get(email);
        if (entry == null || Instant.now().isAfter(entry.expiry)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: Code expired or invalid");
        }

        if (!entry.code.equals(code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: Incorrect code");
        }

        return ResponseEntity.ok("Code verified. You can now reset your password.");
    }

    /** Step 3: Reset password **/
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");

        ResetCodeEntry entry = resetCodes.get(email);
        if (entry == null || !entry.code.equals(code) || Instant.now().isAfter(entry.expiry)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: Invalid reset request");
        }

        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: User not found");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        // Remove used reset code
        resetCodes.remove(email);

        return ResponseEntity.ok("Password reset successfully.");
    }

    // ======================= HELPER =========================

    private static class ResetCodeEntry {
        String code;
        Instant expiry;

        ResetCodeEntry(String code, Instant expiry) {
            this.code = code;
            this.expiry = expiry;
        }
    }
}

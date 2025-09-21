package com.example.plant_sever.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenService {

    private final Map<String, RefreshTokenInfo> tokens = new ConcurrentHashMap<>();
    private final long refreshTokenDurationMs = 1000L * 60 * 60 * 24 * 30;

    public String createRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusMillis(refreshTokenDurationMs);
        tokens.put(token, new RefreshTokenInfo(username, expiry));
        return token;
    }

    public String validateAndGetUsername(String token) {
        RefreshTokenInfo info = tokens.get(token);
        if (info == null) return null;
        if (info.expiry().isBefore(Instant.now())) {
            tokens.remove(token);
            return null;
        }
        return info.username();
    }

    public void revokeToken(String token) {
        tokens.remove(token);
    }

    private record RefreshTokenInfo(String username, Instant expiry) {}
}

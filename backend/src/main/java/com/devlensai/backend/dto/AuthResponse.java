package com.devlensai.backend.dto;

import java.time.Instant;

public record AuthResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {

    @Override
    public String toString() {
        return "AuthResponse[token=[REDACTED], tokenType=" + tokenType
                + ", expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}

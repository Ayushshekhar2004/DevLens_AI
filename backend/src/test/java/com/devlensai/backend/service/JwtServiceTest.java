package com.devlensai.backend.service;

import com.devlensai.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @Test
    void generatesVerifiableTokenWithExpectedClaims() {
        JwtService service = new JwtService(SECRET, 60);
        User user = new User("Ada", "ada@example.com", "bcrypt-hash");

        JwtService.GeneratedToken generated = service.generateToken(user);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(generated.value())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("ada@example.com");
        assertThat(claims.getIssuer()).isEqualTo("devlens-ai");
        assertThat(claims.get("name", String.class)).isEqualTo("Ada");
        assertThat(generated.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(service.extractSubject(generated.value())).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsShortSecretAndInvalidExpiration() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtService(SECRET, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("greater than zero");
    }
}

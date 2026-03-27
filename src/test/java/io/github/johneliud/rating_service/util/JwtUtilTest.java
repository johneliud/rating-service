package io.github.johneliud.rating_service.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!";

    private JwtUtil jwtUtil;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String buildToken(String subject, String role, long expirationMillis) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    @Test
    void extractUserId_returnsSubjectClaim() {
        String token = buildToken("user-123", "ROLE_USER", 60_000);

        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-123");
    }

    @Test
    void extractRole_returnsRoleClaim() {
        String token = buildToken("user-123", "ROLE_USER", 60_000);

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = buildToken("user-123", "ROLE_USER", 60_000);

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        String token = buildToken("user-123", "ROLE_USER", -1_000);

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = buildToken("user-123", "ROLE_USER", 60_000) + "tampered";

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForBlankToken() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void extractAllClaims_throwsForInvalidToken() {
        assertThatThrownBy(() -> jwtUtil.extractAllClaims("invalid.token.value"))
                .isInstanceOf(Exception.class);
    }
}
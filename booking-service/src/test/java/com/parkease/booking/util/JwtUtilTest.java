package com.parkease.booking.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "parkease-super-secret-key-minimum-256-bits-long!!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", secret);
    }

    private String generateToken(String email, String role) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldExtractEmailFromToken() {
        String token = generateToken("aryan@test.com", "DRIVER");
        assertEquals("aryan@test.com", jwtUtil.getEmail(token));
    }

    @Test
    void shouldExtractRoleFromToken() {
        String token = generateToken("aryan@test.com", "DRIVER");
        assertEquals("DRIVER", jwtUtil.getRole(token));
    }

    @Test
    void shouldValidateValidToken() {
        String token = generateToken("aryan@test.com", "DRIVER");
        assertTrue(jwtUtil.validate(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.validate("invalid.token.here"));
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String expiredToken = Jwts.builder()
                .setSubject("aryan@test.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 10000))
                .setExpiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertFalse(jwtUtil.validate(expiredToken));
    }

    @Test
    void shouldReturnFalseForEmptyToken() {
        assertFalse(jwtUtil.validate(""));
    }
}

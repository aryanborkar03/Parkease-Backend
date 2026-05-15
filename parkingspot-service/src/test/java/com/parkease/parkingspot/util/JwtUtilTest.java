package com.parkease.parkingspot.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setUp() {
        String secret = "thisisaverylongsecretkeyforjwttestingpurposesonly1234567890";
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", secret);

        Key key = Keys.hmacShaKeyFor(secret.getBytes());

        validToken = Jwts.builder()
                .setSubject("test@test.com")
                .claim("role", "DRIVER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        expiredToken = Jwts.builder()
                .setSubject("test@test.com")
                .claim("role", "DRIVER")
                .setIssuedAt(new Date(System.currentTimeMillis() - 10000))
                .setExpiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void getEmailFromToken() {
        assertEquals("test@test.com", jwtUtil.getEmailFromToken(validToken));
    }

    @Test
    void getRoleFromToken() {
        assertEquals("DRIVER", jwtUtil.getRoleFromToken(validToken));
    }

    @Test
    void validateToken_ValidToken() {
        assertTrue(jwtUtil.validateToken(validToken));
    }

    @Test
    void validateToken_ExpiredToken() {
        assertFalse(jwtUtil.validateToken(expiredToken));
    }

    @Test
    void validateToken_MalformedToken() {
        assertFalse(jwtUtil.validateToken("invalid_token_format"));
    }
}

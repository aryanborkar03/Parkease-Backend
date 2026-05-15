package com.parkease.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkease.auth.dto.response.AuthResponse;

public class TestJson {
    public static void main(String[] args) throws Exception {
        AuthResponse resp = AuthResponse.builder().isSuspended(true).build();
        System.out.println("JSON_OUTPUT: " + new ObjectMapper().writeValueAsString(resp));
    }
}

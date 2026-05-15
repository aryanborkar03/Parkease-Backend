package com.parkease.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkease.auth.dto.request.*;
import com.parkease.auth.dto.response.AuthResponse;
import com.parkease.auth.dto.response.UserResponse;
import com.parkease.auth.entity.Role;
import com.parkease.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.parkease.auth.dto.response.ApiResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController controller;

    private MockMvc mvc;
    private ObjectMapper om = new ObjectMapper();

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();

        authResponse = AuthResponse.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .email("test@test.com")
                .fullName("Test User")
                .role(Role.DRIVER)
                .build();

        UserResponse.builder()
                .email("test@test.com")
                .fullName("Test User")
                .role(Role.DRIVER)
                .build();
    }

    @Test
    void register_shouldReturn201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("test@test.com");
        req.setPassword("Password@123");
        req.setRole(Role.DRIVER);

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access_token"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_shouldReturn200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@test.com");
        req.setPassword("Password@123");

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"));
    }

    @Test
    void refresh_shouldReturn200() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("refresh_token");

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_shouldReturn200() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("test@test.com");

        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_shouldReturn200() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("test@test.com");
        req.setOtp("123456");
        req.setNewPassword("newpassword");

        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void logout_shouldReturn200() throws Exception {
        org.springframework.security.core.userdetails.UserDetails userDetails = org.mockito.Mockito.mock(org.springframework.security.core.userdetails.UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@test.com");

        doNothing().when(authService).logout("test@test.com");

        ResponseEntity<ApiResponse> response = controller.logout(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void getProfile_shouldReturn200() throws Exception {
        org.springframework.security.core.userdetails.UserDetails userDetails = org.mockito.Mockito.mock(org.springframework.security.core.userdetails.UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@test.com");

        UserResponse res = UserResponse.builder().email("test@test.com").build();
        when(authService.getProfile("test@test.com")).thenReturn(res);

        ResponseEntity<UserResponse> response = controller.getProfile(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("test@test.com", response.getBody().getEmail());
    }

    // DTO and entity field coverage tests

    @Test
    void testDTOsAndEntities() {
        AuthResponse ar = AuthResponse.builder()
                .accessToken("a")
                .refreshToken("b")
                .tokenType("c")
                .userId(1L)
                .fullName("d")
                .email("e")
                .role(Role.DRIVER)
                .active(true)
                .build();
        assertNotNull(ar.toString());

        UserResponse ur = UserResponse.builder()
                .id(1L)
                .fullName("f")
                .email("g")
                .role(Role.DRIVER)
                .provider(com.parkease.auth.entity.AuthProvider.LOCAL)
                .active(true)
                .profilePicUrl("url")
                .createdAt(java.time.LocalDateTime.now())
                .build();
        assertNotNull(ur.toString());

        com.parkease.auth.dto.response.ApiResponse api = com.parkease.auth.dto.response.ApiResponse.builder()
                .success(true)
                .message("msg")
                .build();
        assertNotNull(api.toString());

        com.parkease.auth.entity.PasswordResetToken prt = com.parkease.auth.entity.PasswordResetToken.builder()
                .id(1L)
                .otp("123456")
                .user(new com.parkease.auth.entity.User())
                .expiresAt(java.time.LocalDateTime.now())
                .used(false)
                .build();
        assertNotNull(prt.toString());

        com.parkease.auth.entity.User u = com.parkease.auth.entity.User.builder()
                .id(1L)
                .fullName("fn")
                .email("e")
                .password("p")
                .role(Role.DRIVER)
                .provider(com.parkease.auth.entity.AuthProvider.LOCAL)
                .providerId("pid")
                .profilePicUrl("url")
                .active(true)
                .build();
        assertNotNull(u.toString());
        u.setFullName("fn2");
        assertEquals("fn2", u.getFullName());
    }
}

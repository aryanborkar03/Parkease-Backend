package com.parkease.auth.security;

import com.parkease.auth.entity.AuthProvider;
import com.parkease.auth.entity.Role;
import com.parkease.auth.entity.User;
import com.parkease.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserDetailsServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).fullName("Test User").email("test@test.com")
                .password("encodedPassword").role(Role.DRIVER)
                .provider(AuthProvider.LOCAL).active(true)
                .build();
    }

    @Test
    void loadUserByUsername_activeUser_shouldReturnUserDetails() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("test@test.com");

        assertNotNull(details);
        assertEquals("test@test.com", details.getUsername());
        assertEquals("encodedPassword", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER")));
    }

    @Test
    void loadUserByUsername_inactiveUser_shouldReturnLockedAccount() {
        user.setActive(false);
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("test@test.com");

        assertFalse(details.isEnabled());
        assertFalse(details.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_notFound_shouldThrowUsernameNotFoundException() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown@test.com"));
    }

    @Test
    void loadUserByUsername_oauthUserNullPassword_shouldUseEmptyPassword() {
        user.setPassword(null);
        user.setProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("oauth@test.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("oauth@test.com");

        assertEquals("", details.getPassword());
    }

    @Test
    void loadUserByUsername_adminRole_shouldHaveAdminAuthority() {
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("admin@test.com");

        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    // Tests for JWT authentication filter behavior

    @Test
    void testJwtAuthFilterValidToken() throws Exception {
        com.parkease.auth.util.JwtUtil jwtUtil = mock(com.parkease.auth.util.JwtUtil.class);
        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain filterChain = mock(jakarta.servlet.FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("token")).thenReturn("test@test.com");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isEnabled()).thenReturn(true);
        when(userDetailsService.loadUserByUsername("test@test.com")).thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testJwtAuthFilterSuspendedUser() throws Exception {
        com.parkease.auth.util.JwtUtil jwtUtil = mock(com.parkease.auth.util.JwtUtil.class);
        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain filterChain = mock(jakarta.servlet.FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("token")).thenReturn("test@test.com");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isEnabled()).thenReturn(false);
        when(userDetailsService.loadUserByUsername("test@test.com")).thenReturn(userDetails);
        
        java.io.PrintWriter writer = mock(java.io.PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, filterChain);
        verify(response).setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void testJwtAuthFilterShouldNotFilter() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(null, null);
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/oauth2/authorization/google");
        
        assertTrue((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", request));
    }

    // Tests for OAuth2 success handler redirect and token generation

    @Test
    void testOAuth2SuccessHandler() throws Exception {
        UserRepository repo = mock(UserRepository.class);
        com.parkease.auth.util.JwtUtil jwt = mock(com.parkease.auth.util.JwtUtil.class);
        com.parkease.auth.service.RefreshTokenService tokenService = mock(com.parkease.auth.service.RefreshTokenService.class);

        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(repo, jwt, tokenService);
        org.springframework.test.util.ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:3000");

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
        org.springframework.security.core.Authentication authentication = mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.oauth2.core.user.OAuth2User oAuth2User = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("oauth@test.com");
        when(oAuth2User.getAttribute("name")).thenReturn("OAuth User");
        when(oAuth2User.getAttribute("picture")).thenReturn("pic");
        when(oAuth2User.getAttribute("sub")).thenReturn("123");

        User newUser = new User();
        newUser.setId(1L);
        newUser.setEmail("oauth@test.com");
        newUser.setRole(Role.DRIVER);
        newUser.setActive(true);

        when(repo.findByEmail("oauth@test.com")).thenReturn(Optional.empty());
        when(repo.save(any(User.class))).thenReturn(newUser);
        when(jwt.generateToken(anyString(), anyString())).thenReturn("token");

        com.parkease.auth.entity.RefreshToken rt = new com.parkease.auth.entity.RefreshToken();
        rt.setToken("ref_token");
        when(tokenService.createRefreshToken(any())).thenReturn(rt);
        when(response.encodeRedirectURL(anyString())).thenAnswer(i -> i.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);
        verify(response).sendRedirect(anyString());
    }
}

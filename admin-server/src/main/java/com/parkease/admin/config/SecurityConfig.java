package com.parkease.admin.config;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the Spring Boot Admin Server.
 *
 * Permits access to the Admin UI (login page, static assets, actuator endpoints)
 * while requiring HTTP Basic authentication for all other requests.
 * CSRF is enabled with cookie-based tokens to support the Admin UI's SPA login flow.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminServerProperties adminServer;

    public SecurityConfig(AdminServerProperties adminServer) {
        this.adminServer = adminServer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(adminServer.path("/"));

        http
            .authorizeHttpRequests(auth -> auth
                // Permit the login page, static assets, and actuator health endpoint
                .requestMatchers(
                    new AntPathRequestMatcher(adminServer.path("/assets/**")),
                    new AntPathRequestMatcher(adminServer.path("/variables.css")),
                    new AntPathRequestMatcher(adminServer.path("/actuator/info")),
                    new AntPathRequestMatcher(adminServer.path("/actuator/health")),
                    new AntPathRequestMatcher(adminServer.path("/login"))
                ).permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage(adminServer.path("/login"))
                .successHandler(successHandler)
            )
            .logout(logout -> logout
                .logoutUrl(adminServer.path("/logout"))
            )
            .httpBasic(basic -> {})
            // Use cookie-based CSRF tokens (required for the Admin UI's REST calls)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher(adminServer.path("/instances")),
                    new AntPathRequestMatcher(adminServer.path("/instances/**")),
                    new AntPathRequestMatcher(adminServer.path("/actuator/**"))
                )
            );

        return http.build();
    }
}

package com.parkease.auth.config;

import com.parkease.auth.entity.AuthProvider;
import com.parkease.auth.entity.Role;
import com.parkease.auth.entity.User;
import com.parkease.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the default admin account on startup when ENABLE_DEFAULT_ADMIN=true.
 * Credentials are read exclusively from environment variables (ADMIN_EMAIL, ADMIN_PASSWORD).
 * No credentials are hardcoded here or in any SQL file.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.enable-default-admin:false}")
    private boolean enableDefaultAdmin;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:Admin User}")
    private String adminFullName;

    @Override
    public void run(String... args) {
        if (!enableDefaultAdmin) {
            log.info("Default admin seeding is disabled (ENABLE_DEFAULT_ADMIN=false).");
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("ENABLE_DEFAULT_ADMIN is true but ADMIN_EMAIL or ADMIN_PASSWORD is not set. Skipping admin seed.");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists for: {}. Skipping seed.", adminEmail);
            return;
        }

        User admin = User.builder()
                .fullName(adminFullName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .provider(AuthProvider.LOCAL)
                .active(true)
                .build();

        userRepository.save(admin);
        log.info("Default admin account created for: {}", adminEmail);
    }
}

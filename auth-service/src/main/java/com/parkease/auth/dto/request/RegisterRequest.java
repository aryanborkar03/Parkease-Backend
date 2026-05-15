package com.parkease.auth.dto.request;

import com.parkease.auth.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Pattern(
        regexp = "^[A-Za-z ]{2,50}$",
        message = "Name can only contain letters and spaces (2-50 characters)"
    )
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address (e.g. name@example.com)")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$",
        message = "Password must be at least 8 characters and include at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    private Role role = Role.DRIVER;
}
package com.parkease.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@parkease.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");
    }

    @Test
    void shouldSendPasswordResetEmailSuccessfully() {
        emailService.sendPasswordResetEmail("aryan@test.com", "Aryan", "123456");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals("noreply@parkease.com", message.getFrom());
        assertEquals("aryan@test.com", message.getTo()[0]);
        assertEquals("Your ParkEase Password Reset OTP", message.getSubject());
        assertTrue(message.getText().contains("123456"));
        assertTrue(message.getText().contains("Hi Aryan"));
    }

    @Test
    void shouldSendWelcomeEmailSuccessfully() {
        emailService.sendWelcomeEmail("aryan@test.com", "Aryan");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals("noreply@parkease.com", message.getFrom());
        assertEquals("aryan@test.com", message.getTo()[0]);
        assertEquals("Welcome to ParkEase!", message.getSubject());
        assertTrue(message.getText().contains("Hi Aryan"));
        assertTrue(message.getText().contains("Welcome to ParkEase"));
    }

    @Test
    void shouldHandleMailFailureGracefullyForResetEmail() {
        doThrow(new RuntimeException("SMTP error"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                emailService.sendPasswordResetEmail("aryan@test.com", "Aryan", "123456"));
    }

    @Test
    void shouldHandleMailFailureGracefullyForWelcomeEmail() {
        doThrow(new RuntimeException("SMTP error"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                emailService.sendWelcomeEmail("aryan@test.com", "Aryan"));
    }
}
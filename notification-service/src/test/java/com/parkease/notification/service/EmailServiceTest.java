package com.parkease.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@parkease.com");
    }

    @Test
    void sendEmail_Success() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(templateEngine.process(eq("notification"), any(Context.class))).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(mimeMessage);

        emailService.sendEmail("test@test.com", "Subject", "Title", "Message");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_Exception() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(templateEngine.process(eq("notification"), any(Context.class))).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        
        // MimeMessageHelper.setSubject calls mimeMessage.setSubject(subject, encoding)
        doThrow(new jakarta.mail.MessagingException("Mail error")).when(mimeMessage).setSubject(anyString(), anyString());

        // It should log and not throw
        emailService.sendEmail("test@test.com", "Subject", "Title", "Message");

        verify(mailSender, never()).send(mimeMessage);
    }
}

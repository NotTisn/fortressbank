package com.uit.userservice.service;

import com.uit.sharedkernel.exception.AppException;
import com.uit.userservice.config.EmailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailServiceImpl
 * Tests email sending functionality for OTP and welcome emails
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl Unit Tests")
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailConfig emailConfig;

    @Mock
    private EmailConfig.OtpConfig otpConfig;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailServiceImpl emailService;

    private String testEmail;
    private String testOtp;
    private int testExpiryMinutes;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testOtp = "123456";
        testExpiryMinutes = 5;

        // Mock email config with lenient()
        lenient().when(emailConfig.getFrom()).thenReturn("noreply@fortressbank.com");
        lenient().when(emailConfig.getFromName()).thenReturn("Fortress Bank");
        lenient().when(emailConfig.getOtp()).thenReturn(otpConfig);
        lenient().when(otpConfig.getSubject()).thenReturn("Your OTP Code");

        // Mock JavaMailSender
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ===== sendOtpEmail Tests =====

    @Test
    @DisplayName("sendOtpEmail() should send OTP email successfully")
    void testSendOtpEmail_Success() throws Exception {
        // When
        emailService.sendOtpEmail(testEmail, testOtp, testExpiryMinutes);

        // Give async time to complete
        Thread.sleep(500);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    // ===== sendWelcomeEmail Tests =====

    @Test
    @DisplayName("sendWelcomeEmail() should send welcome email successfully")
    void testSendWelcomeEmail_Success() throws Exception {
        // Given
        String fullName = "John Doe";

        // When
        emailService.sendWelcomeEmail(testEmail, fullName);

        // Give async time to complete
        Thread.sleep(500);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}

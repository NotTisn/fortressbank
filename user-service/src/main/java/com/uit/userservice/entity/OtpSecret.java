package com.uit.userservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity for storing TOTP secrets for Smart OTP (Google Authenticator compatible).
 * Each user can have one active TOTP secret.
 * 
 * Security considerations:
 * - Secret is stored encrypted (AES-256)
 * - Only ACTIVE secrets can be used for verification
 * - Recovery codes allow account recovery if device is lost
 */
@Entity
@Table(name = "otp_secrets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpSecret {

    @Id
    @Column(name = "user_id")
    private String userId;

    /**
     * The TOTP secret key, encrypted with AES-256.
     * This is the shared secret between the server and the authenticator app.
     */
    @Column(name = "otp_secret_key_encrypt", nullable = false, length = 255)
    private String secretKeyEncrypted;

    /**
     * Status of the TOTP secret.
     * PENDING: User has started enrollment but not confirmed
     * ACTIVE: User has successfully verified the secret
     * DISABLED: User has disabled TOTP
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OtpSecretStatus status = OtpSecretStatus.PENDING;

    /**
     * Timestamp when the secret was created (enrollment started)
     */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Timestamp when the secret was confirmed/activated
     */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /**
     * Number of failed verification attempts (for rate limiting)
     */
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0;

    /**
     * Last failed verification timestamp (for lockout)
     */
    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;

    /**
     * Recovery codes (comma-separated, hashed) for account recovery
     */
    @Column(name = "recovery_codes", length = 1000)
    private String recoveryCodes;

    public enum OtpSecretStatus {
        PENDING,    // Enrollment started, not confirmed
        ACTIVE,     // Confirmed and working
        DISABLED    // User disabled TOTP
    }

    /**
     * Increment failed attempts counter
     */
    public void recordFailedAttempt() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;
        this.lastFailedAt = LocalDateTime.now();
    }

    /**
     * Reset failed attempts after successful verification
     */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.lastFailedAt = null;
    }

    /**
     * Check if locked out due to too many failed attempts
     */
    public boolean isLockedOut() {
        if (failedAttempts == null || failedAttempts < 5) {
            return false;
        }
        if (lastFailedAt == null) {
            return false;
        }
        // Lock out for 15 minutes after 5 failed attempts
        return lastFailedAt.plusMinutes(15).isAfter(LocalDateTime.now());
    }
}

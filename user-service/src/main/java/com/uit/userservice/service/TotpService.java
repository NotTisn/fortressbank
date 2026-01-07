package com.uit.userservice.service;

import com.uit.userservice.dto.totp.*;
import com.uit.userservice.entity.OtpSecret;
import com.uit.userservice.repository.OtpSecretRepository;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/**
 * Service for TOTP (Time-based One-Time Password) management.
 * Provides Google Authenticator compatible Smart OTP functionality.
 * 
 * Features:
 * - Generate and store TOTP secrets (encrypted)
 * - Generate QR codes for authenticator app enrollment
 * - Verify TOTP codes
 * - Generate recovery codes
 * - Lockout after failed attempts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TotpService {

    private static final String ISSUER = "FortressBank";
    private static final int SECRET_LENGTH = 32;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final OtpSecretRepository otpSecretRepository;

    @Value("${totp.encryption-key:FortressBankSecretKey32Bytes!!}")
    private String encryptionKey;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    /**
     * Start TOTP enrollment for a user.
     * Generates a new secret, QR code, and recovery codes.
     */
    @Transactional
    public TotpEnrollmentResponse enrollTotp(String userId, String userEmail) {
        log.info("Starting TOTP enrollment for user: {}", userId);

        // Check if user already has TOTP
        otpSecretRepository.findByUserId(userId).ifPresent(existing -> {
            if (existing.getStatus() == OtpSecret.OtpSecretStatus.ACTIVE) {
                throw new AppException(ErrorCode.BAD_REQUEST, 
                    "TOTP is already active. Please disable it first before re-enrolling.");
            }
            // Remove pending enrollment
            otpSecretRepository.delete(existing);
        });

        // Generate new secret
        String secret = secretGenerator.generate();

        // Generate recovery codes
        List<String> recoveryCodes = generateRecoveryCodes();
        String recoveryCodesHashed = hashRecoveryCodes(recoveryCodes);

        // Encrypt and store
        String encryptedSecret = encrypt(secret);
        OtpSecret otpSecret = OtpSecret.builder()
                .userId(userId)
                .secretKeyEncrypted(encryptedSecret)
                .status(OtpSecret.OtpSecretStatus.PENDING)
                .recoveryCodes(recoveryCodesHashed)
                .createdAt(LocalDateTime.now())
                .build();
        otpSecretRepository.save(otpSecret);

        // Generate QR code
        String qrCodeDataUri = generateQrCode(secret, userEmail);

        // Build provisioning URI
        String otpAuthUri = buildOtpAuthUri(secret, userEmail);

        log.info("TOTP enrollment started for user: {}", userId);

        return TotpEnrollmentResponse.builder()
                .secretKey(secret)
                .qrCodeDataUri(qrCodeDataUri)
                .otpAuthUri(otpAuthUri)
                .recoveryCodes(recoveryCodes)
                .instructions("1. Scan the QR code with your authenticator app (Google Authenticator, Authy, etc.)\n" +
                             "2. Enter the 6-digit code from the app to confirm enrollment\n" +
                             "3. Save your recovery codes in a safe place")
                .status("PENDING")
                .build();
    }

    /**
     * Confirm TOTP enrollment by verifying a code from the authenticator app.
     */
    @Transactional
    public TotpVerifyResponse confirmEnrollment(String userId, String code) {
        log.info("Confirming TOTP enrollment for user: {}", userId);

        OtpSecret otpSecret = otpSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TOTP_NOT_ENROLLED, 
                    "No TOTP enrollment found. Please start enrollment first."));

        if (otpSecret.getStatus() == OtpSecret.OtpSecretStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "TOTP is already active.");
        }

        // Verify the code
        String secret = decrypt(otpSecret.getSecretKeyEncrypted());
        boolean isValid = codeVerifier.isValidCode(secret, code);

        if (isValid) {
            // Activate TOTP
            otpSecret.setStatus(OtpSecret.OtpSecretStatus.ACTIVE);
            otpSecret.setActivatedAt(LocalDateTime.now());
            otpSecret.resetFailedAttempts();
            otpSecretRepository.save(otpSecret);

            log.info("TOTP enrollment confirmed for user: {}", userId);

            return TotpVerifyResponse.builder()
                    .valid(true)
                    .message("TOTP has been successfully activated!")
                    .userId(userId)
                    .build();
        } else {
            otpSecret.recordFailedAttempt();
            otpSecretRepository.save(otpSecret);

            log.warn("Invalid TOTP code during enrollment confirmation for user: {}", userId);

            return TotpVerifyResponse.builder()
                    .valid(false)
                    .message("Invalid code. Please try again with the current code from your app.")
                    .userId(userId)
                    .remainingAttempts(MAX_FAILED_ATTEMPTS - otpSecret.getFailedAttempts())
                    .build();
        }
    }

    /**
     * Verify a TOTP code for an active user.
     * Used during high-risk transaction verification.
     */
    @Transactional
    public TotpVerifyResponse verifyCode(String userId, String code, String transactionId) {
        log.info("Verifying TOTP for user: {} transaction: {}", userId, transactionId);

        OtpSecret otpSecret = otpSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TOTP_NOT_ENROLLED, 
                    "TOTP not enrolled. Please set up Smart OTP first."));

        if (otpSecret.getStatus() != OtpSecret.OtpSecretStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, 
                "TOTP is not active. Status: " + otpSecret.getStatus());
        }

        // Check lockout
        if (otpSecret.isLockedOut()) {
            log.warn("TOTP verification blocked - user is locked out: {}", userId);
            return TotpVerifyResponse.builder()
                    .valid(false)
                    .message("Too many failed attempts. Please try again in " + LOCKOUT_MINUTES + " minutes.")
                    .userId(userId)
                    .transactionId(transactionId)
                    .lockedOut(true)
                    .build();
        }

        // Verify the code
        String secret = decrypt(otpSecret.getSecretKeyEncrypted());
        boolean isValid = codeVerifier.isValidCode(secret, code);

        if (isValid) {
            otpSecret.resetFailedAttempts();
            otpSecretRepository.save(otpSecret);

            log.info("TOTP verification successful for user: {} transaction: {}", userId, transactionId);

            return TotpVerifyResponse.builder()
                    .valid(true)
                    .message("TOTP verified successfully")
                    .userId(userId)
                    .transactionId(transactionId)
                    .build();
        } else {
            otpSecret.recordFailedAttempt();
            otpSecretRepository.save(otpSecret);
            int remaining = MAX_FAILED_ATTEMPTS - otpSecret.getFailedAttempts();

            log.warn("Invalid TOTP for user: {} transaction: {} - {} attempts remaining", 
                    userId, transactionId, remaining);

            return TotpVerifyResponse.builder()
                    .valid(false)
                    .message(remaining > 0 
                        ? "Invalid code. " + remaining + " attempts remaining."
                        : "Account locked for " + LOCKOUT_MINUTES + " minutes due to too many failed attempts.")
                    .userId(userId)
                    .transactionId(transactionId)
                    .remainingAttempts(Math.max(0, remaining))
                    .lockedOut(remaining <= 0)
                    .build();
        }
    }

    /**
     * Get TOTP status for a user.
     */
    public TotpStatusResponse getStatus(String userId) {
        return otpSecretRepository.findByUserId(userId)
                .map(secret -> TotpStatusResponse.builder()
                        .enrolled(secret.getStatus() == OtpSecret.OtpSecretStatus.ACTIVE)
                        .status(secret.getStatus().name())
                        .activatedAt(secret.getActivatedAt() != null 
                            ? secret.getActivatedAt().toString() : null)
                        .lockedOut(secret.isLockedOut())
                        .message(getStatusMessage(secret))
                        .build())
                .orElse(TotpStatusResponse.builder()
                        .enrolled(false)
                        .status("NONE")
                        .message("Smart OTP not enrolled. Enroll to secure high-value transactions.")
                        .build());
    }

    /**
     * Disable TOTP for a user.
     */
    @Transactional
    public void disableTotp(String userId) {
        OtpSecret otpSecret = otpSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TOTP_NOT_ENROLLED, "TOTP not enrolled."));

        otpSecret.setStatus(OtpSecret.OtpSecretStatus.DISABLED);
        otpSecretRepository.save(otpSecret);

        log.info("TOTP disabled for user: {}", userId);
    }

    /**
     * Verify a recovery code and disable TOTP (for account recovery).
     */
    @Transactional
    public boolean useRecoveryCode(String userId, String recoveryCode) {
        OtpSecret otpSecret = otpSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TOTP_NOT_ENROLLED, "TOTP not enrolled."));

        String hashedInput = hashSingleRecoveryCode(recoveryCode.toUpperCase().replace("-", ""));
        String[] storedCodes = otpSecret.getRecoveryCodes().split(",");

        for (int i = 0; i < storedCodes.length; i++) {
            if (storedCodes[i].equals(hashedInput)) {
                // Mark code as used by replacing with "USED"
                storedCodes[i] = "USED";
                otpSecret.setRecoveryCodes(String.join(",", storedCodes));
                otpSecret.setStatus(OtpSecret.OtpSecretStatus.DISABLED);
                otpSecretRepository.save(otpSecret);

                log.info("Recovery code used for user: {} - TOTP disabled", userId);
                return true;
            }
        }

        log.warn("Invalid recovery code attempt for user: {}", userId);
        return false;
    }

    // ==================== Private Helper Methods ====================

    private String generateQrCode(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(data);
            return getDataUriForImage(imageData, generator.getImageMimeType());
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Failed to generate QR code");
        }
    }

    private String buildOtpAuthUri(String secret, String email) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                ISSUER, email, secret, ISSUER);
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Exclude confusing chars

        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                if (j == 4) code.append("-"); // Format: XXXX-XXXX
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    private String hashRecoveryCodes(List<String> codes) {
        return codes.stream()
                .map(code -> hashSingleRecoveryCode(code.replace("-", "")))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String hashSingleRecoveryCode(String code) {
        // Simple hash for demo - in production use bcrypt
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, 0, 32, "AES");
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to ciphertext
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Encryption failed");
        }
    }

    private String decrypt(String ciphertext) {
        try {
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, 0, 32, "AES");
            
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[decoded.length - 12];
            System.arraycopy(decoded, 0, iv, 0, 12);
            System.arraycopy(decoded, 12, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Decryption failed");
        }
    }

    private String getStatusMessage(OtpSecret secret) {
        return switch (secret.getStatus()) {
            case PENDING -> "Enrollment started. Please scan the QR code and confirm.";
            case ACTIVE -> secret.isLockedOut() 
                ? "Smart OTP is locked due to failed attempts. Try again in " + LOCKOUT_MINUTES + " minutes."
                : "Smart OTP is active and protecting your high-value transactions.";
            case DISABLED -> "Smart OTP was disabled. Re-enroll for enhanced security.";
        };
    }
}

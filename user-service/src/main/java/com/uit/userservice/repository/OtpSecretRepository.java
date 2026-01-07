package com.uit.userservice.repository;

import com.uit.userservice.entity.OtpSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for OtpSecret entity.
 */
@Repository
public interface OtpSecretRepository extends JpaRepository<OtpSecret, String> {

    /**
     * Find OTP secret by user ID
     */
    Optional<OtpSecret> findByUserId(String userId);

    /**
     * Check if user has an active TOTP secret
     */
    boolean existsByUserIdAndStatus(String userId, OtpSecret.OtpSecretStatus status);

    /**
     * Delete OTP secret for a user (for re-enrollment)
     */
    void deleteByUserId(String userId);
}

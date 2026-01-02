package com.uit.transactionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to verify device biometric signature for a transaction.
 * Used for DEVICE_BIO challenge type (Vietnamese e-banking style).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDeviceSignatureRequest {
    
    /**
     * Transaction ID to verify.
     */
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;
    
    /**
     * Device ID that signed the challenge.
     */
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    
    /**
     * Base64-encoded signature of the challenge data.
     * Created by device's secure key after biometric unlock.
     */
    @NotBlank(message = "Signature is required")
    private String signatureBase64;
}

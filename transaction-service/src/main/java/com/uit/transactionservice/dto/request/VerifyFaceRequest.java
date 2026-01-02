package com.uit.transactionservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to verify face for a transaction.
 * Used for FACE_VERIFY challenge type (Vietnamese e-banking style high-risk).
 * Note: Actual face images are sent as multipart form data, not in this JSON body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyFaceRequest {
    
    /**
     * Transaction ID to verify.
     */
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;
}

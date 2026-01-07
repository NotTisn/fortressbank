package com.uit.transactionservice.dto.request;

import com.uit.transactionservice.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransferRequest {

    @NotBlank(message = "Sender account ID is required")
    private String senderAccountId;



    @NotBlank(message = "Sender account Number is required")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account Number is required")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    private String description;

    /**
     * Device fingerprint for risk assessment (optional).
     * Used to detect new/suspicious devices.
     */
    private String deviceFingerprint;

    /**
     * Client IP address for risk assessment (optional).
     * Populated by the API gateway or controller.
     */
    private String ipAddress;
}

package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BeneficiaryRequest(
        @NotBlank(message = "Account number is required")
        String accountNumber,
        
        String accountName,
        
        @NotBlank(message = "Bank name is required")
        String bankName,
        
        String nickName  
) {}

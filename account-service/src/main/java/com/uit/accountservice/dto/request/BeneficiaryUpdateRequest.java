package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.Size;

public record BeneficiaryUpdateRequest(
        @Size(max = 50, message = "Nickname too long")
        String nickName,
        
        @Size(max = 100, message = "Account name too long")
        String accountName
) {}

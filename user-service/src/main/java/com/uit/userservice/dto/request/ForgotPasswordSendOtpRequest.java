package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordSendOtpRequest {
    @NotBlank(message = "PHONE_NUMBER_REQUIRED")
    @Pattern(regexp = "^\\+84[0-9]{9,10}$", message = "PHONE_NUMBER_INVALID_FORMAT")
    private String phoneNumber;
}

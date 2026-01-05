package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordVerifyOtpRequest {
    @NotBlank(message = "PHONE_NUMBER_REQUIRED")
    @Pattern(regexp = "^\\+84[0-9]{9,10}$", message = "PHONE_NUMBER_INVALID_FORMAT")
    private String phoneNumber;

    @NotBlank(message = "OTP_REQUIRED")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP_INVALID_FORMAT")
    private String otp;
}

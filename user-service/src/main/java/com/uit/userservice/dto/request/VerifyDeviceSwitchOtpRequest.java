package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDeviceSwitchOtpRequest {

    @NotBlank(message = "USER_ID_REQUIRED")
    private String userId;

    @NotBlank(message = "OTP_REQUIRED")
    @Pattern(regexp = "^\\d{6}$", message = "OTP_MUST_BE_6_DIGITS")
    private String otpCode;
}

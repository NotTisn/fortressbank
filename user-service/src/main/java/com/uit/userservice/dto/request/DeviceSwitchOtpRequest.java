package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSwitchOtpRequest {

    @NotBlank(message = "USER_ID_REQUIRED")
    private String userId;

    @NotBlank(message = "NEW_DEVICE_ID_REQUIRED")
    private String newDeviceId;
}

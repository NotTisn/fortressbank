package com.uit.userservice.dto.device;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceVerifySignatureRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    @NotBlank(message = "Challenge is required")
    private String challenge;
    @NotBlank(message = "Signature is required")
    private String signatureBase64;
}

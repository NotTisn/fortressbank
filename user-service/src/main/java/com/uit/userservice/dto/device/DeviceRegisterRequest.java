package com.uit.userservice.dto.device;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceRegisterRequest {
    @NotBlank
    private String deviceId;
    private String name;
    @NotBlank
    private String publicKeyPem;
}

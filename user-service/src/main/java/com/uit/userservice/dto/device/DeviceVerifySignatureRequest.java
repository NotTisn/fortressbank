package com.uit.userservice.dto.device;

import lombok.Data;

@Data
public class DeviceVerifySignatureRequest {
    private String userId;
    private String deviceId;
    private String challenge;
    private String signatureBase64;
}

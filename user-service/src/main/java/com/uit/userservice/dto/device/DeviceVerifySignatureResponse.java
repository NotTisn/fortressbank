package com.uit.userservice.dto.device;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceVerifySignatureResponse {
    private boolean valid;
    private String message;
}

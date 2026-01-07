package com.uit.userservice.dto.device;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DeviceRegisterResponse {
    private String deviceId;
    private LocalDateTime registeredAt;
}

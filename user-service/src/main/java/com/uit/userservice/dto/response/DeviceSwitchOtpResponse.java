package com.uit.userservice.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSwitchOtpResponse {
    
    private boolean success;
    private String message;
}

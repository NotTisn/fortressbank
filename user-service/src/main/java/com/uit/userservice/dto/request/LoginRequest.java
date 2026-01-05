package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String deviceId  // Optional: if not provided, Keycloak will generate one for browser-based logins
) { }

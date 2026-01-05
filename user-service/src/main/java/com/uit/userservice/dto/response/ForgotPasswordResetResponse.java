package com.uit.userservice.dto.response;

public record ForgotPasswordResetResponse(
    boolean success,
    String message
) { }

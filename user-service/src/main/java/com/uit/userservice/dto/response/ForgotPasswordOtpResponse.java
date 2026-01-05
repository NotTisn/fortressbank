package com.uit.userservice.dto.response;

public record ForgotPasswordOtpResponse(
    boolean sent,
    String message,
    int expirySeconds
) { }

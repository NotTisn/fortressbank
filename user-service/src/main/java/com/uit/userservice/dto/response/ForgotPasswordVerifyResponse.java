package com.uit.userservice.dto.response;

public record ForgotPasswordVerifyResponse(
    boolean verified,
    String message,
    String verificationToken
) { }

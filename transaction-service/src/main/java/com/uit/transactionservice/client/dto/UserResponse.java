package com.uit.transactionservice.client.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * User response DTO from user-service
 * Contains user information including phoneNumber for OTP delivery
 */
public record UserResponse(
        String id,
        String username,
        String email,
        String fullName,
        String citizenId,
        LocalDate dob,
        String phoneNumber,
        LocalDateTime createdAt,
        Boolean isFaceRegistered
) { }

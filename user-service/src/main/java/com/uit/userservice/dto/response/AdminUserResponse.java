package com.uit.userservice.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        String fullName,
        String citizenId,
        LocalDate dob,
        String phoneNumber,
        boolean isEnable,
        LocalDateTime createdAt
) { }
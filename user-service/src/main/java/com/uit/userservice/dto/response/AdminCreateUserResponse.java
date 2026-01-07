package com.uit.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserResponse {

    // User information
    private String userId;
    private String username;
    private String email;
    private String fullName;
    private String citizenId;
    private LocalDate dob;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private Boolean isEnable;

    // Account information
    private AccountDto account;

    // Card information (optional, may be null if card creation failed or not requested)
    private CardDto card;
}

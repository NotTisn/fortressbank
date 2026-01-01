package com.uit.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    private String id;    // keycloak userId

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String fullName;

    private LocalDate dob;

    @Column(unique = true)
    private String citizenId;

    @Column(unique = true)
    private String phoneNumber;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column()
    private Boolean isFaceRegistered = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

package com.uit.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;

    @Column(name = "name", length = 255)
    private String name;

    /**
     * Device public key in PEM format (stored to verify challenge signatures)
     */
    @Column(name = "public_key_pem", columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "is_active")
    private Boolean isActive;
}

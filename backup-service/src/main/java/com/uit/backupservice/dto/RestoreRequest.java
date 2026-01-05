package com.uit.backupservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreRequest {

    @NotNull(message = "Backup ID is required")
    private UUID backupId;

    private List<String> serviceNames; // If null, restore all services in backup

    @Builder.Default
    private Boolean stopServices = true;

    @Builder.Default
    private Boolean clearRedisCache = true;

    @Builder.Default
    private Boolean verifyIntegrity = true;

    private String initiatedBy;
}

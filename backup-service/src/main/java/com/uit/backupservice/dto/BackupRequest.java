package com.uit.backupservice.dto;

import com.uit.backupservice.entity.BackupType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupRequest {

    @NotNull(message = "Backup type is required")
    private BackupType backupType;

    private String backupName;

    private List<String> serviceNames; // For CUSTOM backup type

    @Builder.Default
    private Boolean compressed = true;

    @Builder.Default
    private Boolean encrypted = false;

    private String initiatedBy;
}

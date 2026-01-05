package com.uit.backupservice.dto;

import com.uit.backupservice.entity.BackupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreResponse {

    private UUID backupId;
    private BackupStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private List<String> restoredServices;
    private String errorMessage;
    private Boolean success;
}

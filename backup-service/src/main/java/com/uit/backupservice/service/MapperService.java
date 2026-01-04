package com.uit.backupservice.service;

import com.uit.backupservice.dto.BackupResponse;
import com.uit.backupservice.dto.ServiceBackupDTO;
import com.uit.backupservice.entity.BackupMetadata;
import com.uit.backupservice.entity.ServiceBackupInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapperService {

    private final StorageService storageService;

    public BackupResponse toBackupResponse(BackupMetadata metadata) {
        Long durationMs = null;
        if (metadata.getStartedAt() != null && metadata.getCompletedAt() != null) {
            durationMs = Duration.between(metadata.getStartedAt(), metadata.getCompletedAt()).toMillis();
        }

        return BackupResponse.builder()
                .backupId(metadata.getBackupId())
                .backupName(metadata.getBackupName())
                .backupType(metadata.getBackupType())
                .status(metadata.getStatus())
                .startedAt(metadata.getStartedAt())
                .completedAt(metadata.getCompletedAt())
                .totalSizeBytes(metadata.getTotalSizeBytes())
                .totalSizeFormatted(metadata.getTotalSizeBytes() != null
                        ? storageService.formatBytes(metadata.getTotalSizeBytes())
                        : null)
                .backupPath(metadata.getBackupPath())
                .compressed(metadata.getCompressed())
                .encrypted(metadata.getEncrypted())
                .checksum(metadata.getChecksum())
                .errorMessage(metadata.getErrorMessage())
                .initiatedBy(metadata.getInitiatedBy())
                .serviceBackups(toServiceBackupDTOs(metadata.getServiceBackups()))
                .durationMs(durationMs)
                .createdAt(metadata.getCreatedAt())
                .build();
    }

    public List<ServiceBackupDTO> toServiceBackupDTOs(List<ServiceBackupInfo> serviceBackups) {
        return serviceBackups.stream()
                .map(this::toServiceBackupDTO)
                .collect(Collectors.toList());
    }

    public ServiceBackupDTO toServiceBackupDTO(ServiceBackupInfo info) {
        return ServiceBackupDTO.builder()
                .id(info.getId())
                .serviceName(info.getServiceName())
                .databaseName(info.getDatabaseName())
                .containerName(info.getContainerName())
                .filePath(info.getFilePath())
                .fileSizeBytes(info.getFileSizeBytes())
                .fileSizeFormatted(info.getFileSizeBytes() != null
                        ? storageService.formatBytes(info.getFileSizeBytes())
                        : null)
                .recordCount(info.getRecordCount())
                .checksum(info.getChecksum())
                .status(info.getStatus())
                .errorMessage(info.getErrorMessage())
                .backupDurationMs(info.getBackupDurationMs())
                .build();
    }
}

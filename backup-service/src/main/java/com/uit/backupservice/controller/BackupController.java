package com.uit.backupservice.controller;

import com.uit.backupservice.dto.BackupRequest;
import com.uit.backupservice.dto.BackupResponse;
import com.uit.backupservice.service.BackupService;
import com.uit.backupservice.service.MinIOStorageService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final MinIOStorageService minIOStorageService;

    @PostMapping
    public ResponseEntity<ApiResponse<BackupResponse>> createBackup(@Valid @RequestBody BackupRequest request) {
        log.info("Received backup request: type={}, name={}", request.getBackupType(), request.getBackupName());

        BackupResponse response = backupService.createBackup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BackupResponse>>> getAllBackups() {
        List<BackupResponse> backups = backupService.getAllBackups();

        return ResponseEntity.ok(ApiResponse.success(backups));
    }

    @GetMapping("/{backupId}")
    public ResponseEntity<ApiResponse<BackupResponse>> getBackupById(@PathVariable("backupId") UUID backupId) {
        BackupResponse backup = backupService.getBackupById(backupId);

        return ResponseEntity.ok(ApiResponse.success(backup));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<Void>> cleanupOldBackups() {
        log.info("Manual cleanup of old backups requested");

        backupService.deleteOldBackups();

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{backupId}/cloud-download-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCloudDownloadUrl(
            @PathVariable("backupId") UUID backupId,
            @RequestParam(value = "expirySeconds", defaultValue = "3600") int expirySeconds) {

        log.info("Generating cloud download URL for backup: {}, expiry: {}s", backupId, expirySeconds);

        try {
            if (!minIOStorageService.isAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error(5000, "Cloud storage is not available", null));
            }

            // Get backup metadata to find cloud path
            BackupResponse backup = backupService.getBackupById(backupId);

            if (!Boolean.TRUE.equals(backup.getUploadedToCloud())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(4004, "Backup has not been uploaded to cloud storage", null));
            }

            // Generate presigned URL for the backup directory
            String objectPrefix = "backups/" + backupId + "/";

            // For simplicity, return the cloud storage URL
            // In production, you'd generate presigned URLs for each file
            Map<String, String> response = Map.of(
                    "cloudUrl", backup.getCloudStorageUrl(),
                    "backupId", backupId.toString(),
                    "message", "Access backup files via MinIO Console at http://localhost:9001"
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Failed to generate cloud download URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5000, "Failed to generate download URL: " + e.getMessage(), null));
        }
    }
}

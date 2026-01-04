package com.uit.backupservice.controller;

import com.uit.backupservice.dto.BackupRequest;
import com.uit.backupservice.dto.BackupResponse;
import com.uit.backupservice.service.BackupService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;

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
}

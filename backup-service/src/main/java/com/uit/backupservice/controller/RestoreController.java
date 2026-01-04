package com.uit.backupservice.controller;

import com.uit.backupservice.dto.RestoreRequest;
import com.uit.backupservice.dto.RestoreResponse;
import com.uit.backupservice.service.RestoreService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restore")
@RequiredArgsConstructor
@Slf4j
public class RestoreController {

    private final RestoreService restoreService;

    @PostMapping
    public ResponseEntity<ApiResponse<RestoreResponse>> restoreBackup(@Valid @RequestBody RestoreRequest request) {
        log.info("Received restore request for backup: {}", request.getBackupId());

        RestoreResponse response = restoreService.restoreBackup(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

package com.uit.backupservice.service;

import com.uit.backupservice.config.DatabaseConfig;
import com.uit.backupservice.dto.RestoreRequest;
import com.uit.backupservice.dto.RestoreResponse;
import com.uit.backupservice.entity.BackupMetadata;
import com.uit.backupservice.entity.BackupStatus;
import com.uit.backupservice.entity.ServiceBackupInfo;
import com.uit.backupservice.repository.BackupMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestoreService {

    private final BackupMetadataRepository backupMetadataRepository;
    private final DatabaseConfig databaseConfig;

    public RestoreResponse restoreBackup(RestoreRequest request) {
        log.info("Starting restore process for backup: {}", request.getBackupId());

        LocalDateTime startTime = LocalDateTime.now();
        RestoreResponse.RestoreResponseBuilder responseBuilder = RestoreResponse.builder()
                .backupId(request.getBackupId())
                .startedAt(startTime)
                .status(BackupStatus.RESTORING);

        try {
            // Fetch backup metadata
            BackupMetadata metadata = backupMetadataRepository.findById(request.getBackupId())
                    .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + request.getBackupId()));

            if (metadata.getStatus() != BackupStatus.COMPLETED) {
                throw new IllegalStateException("Cannot restore incomplete backup. Status: " + metadata.getStatus());
            }

            // Validate backup integrity
            if (request.getVerifyIntegrity()) {
                log.info("Verifying backup integrity...");
                validateBackupIntegrity(metadata);
            }

            // Determine services to restore
            List<ServiceBackupInfo> servicesToRestore = determineServicesToRestore(metadata, request.getServiceNames());

            List<String> restoredServices = new ArrayList<>();

            // Restore in dependency order: user-service -> account-service -> transaction-service -> audit-service
            String[] restoreOrder = {"user-service", "account-service", "transaction-service", "audit-service", "notification-service"};

            for (String serviceName : restoreOrder) {
                ServiceBackupInfo serviceInfo = servicesToRestore.stream()
                        .filter(s -> s.getServiceName().equals(serviceName))
                        .findFirst()
                        .orElse(null);

                if (serviceInfo != null) {
                    restoreServiceDatabase(serviceInfo, request.getStopServices());
                    restoredServices.add(serviceName);
                }
            }

            // Clear Redis cache if requested
            if (request.getClearRedisCache()) {
                clearRedisCache();
            }

            LocalDateTime endTime = LocalDateTime.now();
            return responseBuilder
                    .status(BackupStatus.COMPLETED)
                    .completedAt(endTime)
                    .durationMs(java.time.Duration.between(startTime, endTime).toMillis())
                    .restoredServices(restoredServices)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Restore failed: {}", e.getMessage(), e);
            LocalDateTime endTime = LocalDateTime.now();
            return responseBuilder
                    .status(BackupStatus.FAILED)
                    .completedAt(endTime)
                    .durationMs(java.time.Duration.between(startTime, endTime).toMillis())
                    .errorMessage(e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private List<ServiceBackupInfo> determineServicesToRestore(BackupMetadata metadata, List<String> serviceNames) {
        if (serviceNames == null || serviceNames.isEmpty()) {
            return metadata.getServiceBackups();
        }

        return metadata.getServiceBackups().stream()
                .filter(s -> serviceNames.contains(s.getServiceName()))
                .toList();
    }

    private void validateBackupIntegrity(BackupMetadata metadata) throws Exception {
        for (ServiceBackupInfo serviceInfo : metadata.getServiceBackups()) {
            File backupFile = new File(serviceInfo.getFilePath());
            if (!backupFile.exists()) {
                throw new FileNotFoundException("Backup file not found: " + serviceInfo.getFilePath());
            }

            // Verify file is not corrupted (basic check)
            if (backupFile.length() == 0) {
                throw new IOException("Backup file is empty: " + serviceInfo.getFilePath());
            }
        }
        log.info("Backup integrity verified successfully");
    }

    private void restoreServiceDatabase(ServiceBackupInfo serviceInfo, Boolean stopServices) throws Exception {
        log.info("Restoring service: {}", serviceInfo.getServiceName());

        databaseConfig.initDefaultDatabases();
        DatabaseConfig.DatabaseInfo dbInfo = databaseConfig.getDatabases().get(serviceInfo.getServiceName());

        if (dbInfo == null) {
            throw new IllegalArgumentException("Database info not found for service: " + serviceInfo.getServiceName());
        }

        // Check if file is compressed
        boolean isCompressed = serviceInfo.getFilePath().endsWith(".gz");

        // Execute pg_restore via docker exec
        executePgRestore(dbInfo, serviceInfo.getFilePath(), isCompressed);

        log.info("Service restored successfully: {}", serviceInfo.getServiceName());
    }

    private void executePgRestore(DatabaseConfig.DatabaseInfo dbInfo, String backupFilePath, boolean isCompressed) throws Exception {
        File backupFile = new File(backupFilePath);
        if (!backupFile.exists()) {
            throw new FileNotFoundException("Backup file not found: " + backupFilePath);
        }

        // If compressed, decompress first
        String sqlFilePath = backupFilePath;
        if (isCompressed) {
            sqlFilePath = backupFilePath.replace(".gz", "");
            decompressFile(backupFilePath, sqlFilePath);
        }

        // Copy SQL file into container
        String containerPath = "/tmp/" + new File(sqlFilePath).getName();
        copyFileToContainer(dbInfo.getContainerName(), sqlFilePath, containerPath);

        // Execute psql to restore
        String psqlCommand = String.format(
                "PGPASSWORD='%s' psql -h localhost -p 5432 -U %s -d %s -f %s",
                dbInfo.getPassword(),
                dbInfo.getUsername(),
                dbInfo.getDatabaseName(),
                containerPath
        );

        String dockerCommand = String.format(
                "docker exec %s sh -c \"%s\"",
                dbInfo.getContainerName(),
                psqlCommand
        );

        log.debug("Executing restore command for: {}", dbInfo.getDatabaseName());

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", dockerCommand);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("psql output: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("psql restore failed with exit code: " + exitCode);
        }

        // Clean up temporary file in container
        cleanupContainerFile(dbInfo.getContainerName(), containerPath);

        // Delete decompressed file if it was created
        if (isCompressed) {
            new File(sqlFilePath).delete();
        }
    }

    private void decompressFile(String gzipPath, String outputPath) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzipPath));
             FileOutputStream fos = new FileOutputStream(outputPath)) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        log.debug("Decompressed file: {} -> {}", gzipPath, outputPath);
    }

    private void copyFileToContainer(String containerName, String hostPath, String containerPath) throws Exception {
        String dockerCopyCommand = String.format(
                "docker cp %s %s:%s",
                hostPath,
                containerName,
                containerPath
        );

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", dockerCopyCommand);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Failed to copy file to container. Exit code: " + exitCode);
        }

        log.debug("Copied file to container: {} -> {}:{}", hostPath, containerName, containerPath);
    }

    private void cleanupContainerFile(String containerName, String containerPath) {
        try {
            String dockerRmCommand = String.format(
                    "docker exec %s rm -f %s",
                    containerName,
                    containerPath
            );

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("sh", "-c", dockerRmCommand);
            Process process = processBuilder.start();
            process.waitFor();

            log.debug("Cleaned up container file: {}", containerPath);
        } catch (Exception e) {
            log.warn("Failed to cleanup container file: {}", e.getMessage());
        }
    }

    private void clearRedisCache() {
        try {
            String dockerCommand = "docker exec fortressbank-redis redis-cli FLUSHALL";

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("sh", "-c", dockerCommand);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Redis cache cleared successfully");
            } else {
                log.warn("Failed to clear Redis cache");
            }
        } catch (Exception e) {
            log.warn("Error clearing Redis cache: {}", e.getMessage());
        }
    }
}

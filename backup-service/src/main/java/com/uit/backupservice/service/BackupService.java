package com.uit.backupservice.service;

import com.uit.backupservice.config.DatabaseConfig;
import com.uit.backupservice.dto.BackupRequest;
import com.uit.backupservice.dto.BackupResponse;
import com.uit.backupservice.entity.BackupMetadata;
import com.uit.backupservice.entity.BackupStatus;
import com.uit.backupservice.entity.BackupType;
import com.uit.backupservice.entity.ServiceBackupInfo;
import com.uit.backupservice.repository.BackupMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final BackupMetadataRepository backupMetadataRepository;
    private final DatabaseConfig databaseConfig;
    private final StorageService storageService;
    private final MapperService mapperService;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Transactional
    public BackupResponse createBackup(BackupRequest request) {
        log.info("Starting backup process: type={}, name={}", request.getBackupType(), request.getBackupName());

        // Initialize database config if empty
        databaseConfig.initDefaultDatabases();

        // Determine which services to backup
        List<String> servicesToBackup = determineServicesToBackup(request);

        // Create backup metadata
        BackupMetadata metadata = createBackupMetadata(request, servicesToBackup);
        metadata = backupMetadataRepository.save(metadata);

        // Create backup directory
        String backupDirPath = createBackupDirectory(metadata.getBackupId());
        metadata.setBackupPath(backupDirPath);

        try {
            metadata.setStatus(BackupStatus.IN_PROGRESS);
            metadata.setStartedAt(LocalDateTime.now());
            metadata = backupMetadataRepository.save(metadata);

            // Backup each service database
            long totalSize = 0;
            for (String serviceName : servicesToBackup) {
                ServiceBackupInfo serviceInfo = backupServiceDatabase(
                        metadata.getBackupId(),
                        serviceName,
                        backupDirPath,
                        request.getCompressed()
                );
                metadata.addServiceBackup(serviceInfo);
                if (serviceInfo.getStatus() == BackupStatus.COMPLETED) {
                    totalSize += serviceInfo.getFileSizeBytes();
                }
            }

            // Calculate overall checksum
            String checksum = calculateDirectoryChecksum(backupDirPath);
            metadata.setChecksum(checksum);
            metadata.setTotalSizeBytes(totalSize);
            metadata.setCompletedAt(LocalDateTime.now());
            metadata.setStatus(BackupStatus.COMPLETED);

            log.info("Backup completed successfully: id={}, size={} bytes", metadata.getBackupId(), totalSize);

        } catch (Exception e) {
            log.error("Backup failed: {}", e.getMessage(), e);
            metadata.setStatus(BackupStatus.FAILED);
            metadata.setErrorMessage(e.getMessage());
            metadata.setCompletedAt(LocalDateTime.now());
        }

        metadata = backupMetadataRepository.save(metadata);
        return mapperService.toBackupResponse(metadata);
    }

    private List<String> determineServicesToBackup(BackupRequest request) {
        return switch (request.getBackupType()) {
            case FULL -> List.of("user-service", "account-service", "transaction-service", "audit-service");
            case SINGLE_SERVICE -> {
                if (request.getServiceNames() == null || request.getServiceNames().isEmpty()) {
                    throw new IllegalArgumentException("Service name required for SINGLE_SERVICE backup");
                }
                yield List.of(request.getServiceNames().get(0));
            }
            case CUSTOM -> {
                if (request.getServiceNames() == null || request.getServiceNames().isEmpty()) {
                    throw new IllegalArgumentException("Service names required for CUSTOM backup");
                }
                yield request.getServiceNames();
            }
            default -> throw new IllegalArgumentException("Unsupported backup type: " + request.getBackupType());
        };
    }

    private BackupMetadata createBackupMetadata(BackupRequest request, List<String> servicesToBackup) {
        String backupName = request.getBackupName() != null
                ? request.getBackupName()
                : "backup_" + LocalDateTime.now().format(TIMESTAMP_FORMATTER);

        return BackupMetadata.builder()
                .backupName(backupName)
                .backupType(request.getBackupType())
                .status(BackupStatus.PENDING)
                .compressed(request.getCompressed())
                .encrypted(request.getEncrypted())
                .initiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : "system")
                .serviceBackups(new ArrayList<>())
                .build();
    }

    private String createBackupDirectory(UUID backupId) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String dirPath = databaseConfig.getBackupDirectory() + "/" + timestamp + "_" + backupId;

        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dirPath;
    }

    private ServiceBackupInfo backupServiceDatabase(UUID backupId, String serviceName, String backupDirPath, Boolean compressed) {
        log.info("Backing up service: {}", serviceName);

        long startTime = System.currentTimeMillis();
        ServiceBackupInfo info = ServiceBackupInfo.builder()
                .serviceName(serviceName)
                .status(BackupStatus.IN_PROGRESS)
                .build();

        try {
            DatabaseConfig.DatabaseInfo dbInfo = databaseConfig.getDatabases().get(serviceName);
            if (dbInfo == null) {
                throw new IllegalArgumentException("Database info not found for service: " + serviceName);
            }

            info.setDatabaseName(dbInfo.getDatabaseName());
            info.setContainerName(dbInfo.getContainerName());

            // Execute pg_dump via docker exec
            String dumpFileName = serviceName + "_" + dbInfo.getDatabaseName() + ".sql";
            if (compressed) {
                dumpFileName += ".gz";
            }

            String filePath = backupDirPath + "/" + dumpFileName;
            info.setFilePath(filePath);

            executePgDump(dbInfo, filePath, compressed);

            // Get file size and checksum
            File backupFile = new File(filePath);
            info.setFileSizeBytes(backupFile.length());
            info.setChecksum(calculateFileChecksum(filePath));

            // Count records (approximate from file)
            info.setRecordCount(estimateRecordCount(filePath, compressed));

            info.setStatus(BackupStatus.COMPLETED);
            log.info("Service backup completed: {}, size: {} bytes", serviceName, info.getFileSizeBytes());

        } catch (Exception e) {
            log.error("Failed to backup service {}: {}", serviceName, e.getMessage(), e);
            info.setStatus(BackupStatus.FAILED);
            info.setErrorMessage(e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            info.setBackupDurationMs(endTime - startTime);
        }

        return info;
    }

    private void executePgDump(DatabaseConfig.DatabaseInfo dbInfo, String outputPath, Boolean compressed) throws Exception {
        // Build pg_dump command
        String pgDumpCommand = String.format(
                "PGPASSWORD='%s' pg_dump -h localhost -p 5432 -U %s -d %s --clean --if-exists --no-owner --no-acl",
                dbInfo.getPassword(),
                dbInfo.getUsername(),
                dbInfo.getDatabaseName()
        );

        // Build docker exec command
        String dockerCommand = String.format(
                "docker exec %s sh -c \"%s\"",
                dbInfo.getContainerName(),
                pgDumpCommand
        );

        log.debug("Executing docker command: {}", dockerCommand.replace(dbInfo.getPassword(), "***"));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", dockerCommand);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read output and write to file
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            if (compressed) {
                try (FileOutputStream fos = new FileOutputStream(outputPath);
                     GZIPOutputStream gzipOS = new GZIPOutputStream(fos)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        gzipOS.write((line + "\n").getBytes());
                    }
                }
            } else {
                try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        fos.write((line + "\n").getBytes());
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("pg_dump failed with exit code: " + exitCode);
        }
    }

    private String calculateFileChecksum(String filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String calculateDirectoryChecksum(String dirPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File dir = new File(dirPath);
        File[] files = dir.listFiles();

        if (files != null) {
            Arrays.sort(files); // Ensure consistent order
            for (File file : files) {
                if (file.isFile()) {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    digest.update(fileBytes);
                }
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Long estimateRecordCount(String filePath, Boolean compressed) {
        try {
            Path path = Paths.get(filePath);
            long lineCount = 0;

            if (compressed) {
                // For compressed files, estimate based on file size
                // Rough estimate: 1KB per record (very approximate)
                long fileSize = Files.size(path);
                lineCount = fileSize / 1024;
            } else {
                lineCount = Files.lines(path)
                        .filter(line -> line.trim().startsWith("INSERT") || line.trim().startsWith("COPY"))
                        .count();
            }

            return lineCount;
        } catch (Exception e) {
            log.warn("Could not estimate record count: {}", e.getMessage());
            return 0L;
        }
    }

    public List<BackupResponse> getAllBackups() {
        return backupMetadataRepository.findAll().stream()
                .map(mapperService::toBackupResponse)
                .toList();
    }

    public BackupResponse getBackupById(UUID backupId) {
        BackupMetadata metadata = backupMetadataRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + backupId));
        return mapperService.toBackupResponse(metadata);
    }

    @Transactional
    public void deleteOldBackups() {
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(databaseConfig.getRetentionDays());
        List<BackupMetadata> expiredBackups = backupMetadataRepository.findExpiredBackups(expiryDate);

        for (BackupMetadata backup : expiredBackups) {
            try {
                // Delete backup files
                File backupDir = new File(backup.getBackupPath());
                if (backupDir.exists()) {
                    storageService.deleteDirectory(backupDir);
                }

                // Delete metadata
                backupMetadataRepository.delete(backup);
                log.info("Deleted expired backup: {}", backup.getBackupId());
            } catch (Exception e) {
                log.error("Failed to delete backup {}: {}", backup.getBackupId(), e.getMessage());
            }
        }
    }
}

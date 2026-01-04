package com.uit.backupservice.service;

import com.uit.backupservice.dto.BackupRequest;
import com.uit.backupservice.entity.BackupType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final BackupService backupService;

    /**
     * Scheduled automatic full backup - runs daily at 2:00 AM
     */
    @Scheduled(cron = "${backup.schedule-cron:0 0 2 * * *}")
    public void scheduledFullBackup() {
        log.info("Starting scheduled automatic full backup");

        try {
            BackupRequest request = BackupRequest.builder()
                    .backupType(BackupType.FULL)
                    .backupName("scheduled_full_backup")
                    .compressed(true)
                    .encrypted(false)
                    .initiatedBy("scheduler")
                    .build();

            backupService.createBackup(request);
            log.info("Scheduled backup completed successfully");

        } catch (Exception e) {
            log.error("Scheduled backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup old backups - runs daily at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldBackups() {
        log.info("Starting cleanup of old backups");

        try {
            backupService.deleteOldBackups();
            log.info("Cleanup completed successfully");

        } catch (Exception e) {
            log.error("Cleanup failed: {}", e.getMessage(), e);
        }
    }
}

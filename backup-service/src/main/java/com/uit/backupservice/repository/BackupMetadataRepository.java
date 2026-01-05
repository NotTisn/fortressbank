package com.uit.backupservice.repository;

import com.uit.backupservice.entity.BackupMetadata;
import com.uit.backupservice.entity.BackupStatus;
import com.uit.backupservice.entity.BackupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BackupMetadataRepository extends JpaRepository<BackupMetadata, UUID> {

    List<BackupMetadata> findByStatus(BackupStatus status);

    List<BackupMetadata> findByBackupType(BackupType backupType);

    List<BackupMetadata> findByInitiatedBy(String initiatedBy);

    @Query("SELECT b FROM BackupMetadata b WHERE b.createdAt >= :startDate ORDER BY b.createdAt DESC")
    List<BackupMetadata> findRecentBackups(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT b FROM BackupMetadata b WHERE b.status = 'COMPLETED' ORDER BY b.completedAt DESC")
    List<BackupMetadata> findAllCompletedBackups();

    Optional<BackupMetadata> findTopByStatusOrderByCreatedAtDesc(BackupStatus status);

    @Query("SELECT SUM(b.totalSizeBytes) FROM BackupMetadata b WHERE b.status = 'COMPLETED'")
    Long getTotalBackupSize();

    @Query("SELECT b FROM BackupMetadata b WHERE b.createdAt < :expiryDate AND b.status = 'COMPLETED'")
    List<BackupMetadata> findExpiredBackups(@Param("expiryDate") LocalDateTime expiryDate);
}

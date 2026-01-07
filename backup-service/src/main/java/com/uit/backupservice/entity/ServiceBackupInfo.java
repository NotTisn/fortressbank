package com.uit.backupservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "service_backup_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBackupInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backup_id", nullable = false)
    private BackupMetadata backupMetadata;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(name = "container_name", nullable = false)
    private String containerName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "checksum")
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BackupStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "backup_duration_ms")
    private Long backupDurationMs;
}

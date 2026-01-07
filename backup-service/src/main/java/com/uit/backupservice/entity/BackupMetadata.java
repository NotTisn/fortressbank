package com.uit.backupservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "backup_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "backup_id")
    private UUID backupId;

    @Column(name = "backup_name", nullable = false)
    private String backupName;

    @Enumerated(EnumType.STRING)
    @Column(name = "backup_type", nullable = false)
    private BackupType backupType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BackupStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    @Column(name = "backup_path")
    private String backupPath;

    @Column(name = "compressed", nullable = false)
    private Boolean compressed = true;

    @Column(name = "encrypted", nullable = false)
    private Boolean encrypted = false;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "cloud_storage_url", length = 1000)
    private String cloudStorageUrl;

    @Column(name = "uploaded_to_cloud", nullable = false)
    @Builder.Default
    private Boolean uploadedToCloud = false;

    @OneToMany(mappedBy = "backupMetadata", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ServiceBackupInfo> serviceBackups = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addServiceBackup(ServiceBackupInfo serviceBackupInfo) {
        serviceBackups.add(serviceBackupInfo);
        serviceBackupInfo.setBackupMetadata(this);
    }

    public void removeServiceBackup(ServiceBackupInfo serviceBackupInfo) {
        serviceBackups.remove(serviceBackupInfo);
        serviceBackupInfo.setBackupMetadata(null);
    }
}

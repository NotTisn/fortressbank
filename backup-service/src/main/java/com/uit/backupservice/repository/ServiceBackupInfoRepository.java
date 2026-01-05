package com.uit.backupservice.repository;

import com.uit.backupservice.entity.ServiceBackupInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceBackupInfoRepository extends JpaRepository<ServiceBackupInfo, UUID> {

    List<ServiceBackupInfo> findByBackupMetadataBackupId(UUID backupId);

    List<ServiceBackupInfo> findByServiceName(String serviceName);
}

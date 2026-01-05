package com.uit.backupservice.service;

import com.uit.backupservice.config.MinIOConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinIOStorageService implements CloudStorageService {

    private final MinioClient minioClient;
    private final MinIOConfig minioConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            initialize();
        } catch (Exception e) {
            log.error("Failed to initialize MinIO on application startup: {}", e.getMessage());
        }
    }

    @Override
    public void initialize() throws Exception {
        if (!isAvailable()) {
            log.warn("MinIO client is not available. Skipping initialization.");
            return;
        }

        try {
            // Check if bucket exists
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build()
            );

            if (!bucketExists) {
                if (minioConfig.getAutoCreateBucket()) {
                    // Create bucket
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .build()
                    );
                    log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
                } else {
                    throw new IllegalStateException("Bucket does not exist and auto-create is disabled: " + minioConfig.getBucketName());
                }
            } else {
                log.info("MinIO bucket already exists: {}", minioConfig.getBucketName());
            }

            log.info("MinIO storage service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize MinIO storage: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String uploadFile(String localFilePath, String objectName) throws Exception {
        if (!isAvailable()) {
            log.warn("MinIO is not available. Skipping upload for: {}", localFilePath);
            return null;
        }

        try {
            // Determine content type
            String contentType = Files.probeContentType(Paths.get(localFilePath));
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // Upload file
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .filename(localFilePath)
                            .contentType(contentType)
                            .build()
            );

            String cloudUrl = String.format("%s/%s/%s",
                    minioConfig.getEndpoint(),
                    minioConfig.getBucketName(),
                    objectName);

            log.info("Successfully uploaded file to MinIO: {} -> {}", localFilePath, cloudUrl);
            return cloudUrl;

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectName, String contentType, long size) throws Exception {
        if (!isAvailable()) {
            log.warn("MinIO is not available. Skipping upload for: {}", objectName);
            return null;
        }

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );

            String cloudUrl = String.format("%s/%s/%s",
                    minioConfig.getEndpoint(),
                    minioConfig.getBucketName(),
                    objectName);

            log.info("Successfully uploaded stream to MinIO: {}", cloudUrl);
            return cloudUrl;

        } catch (Exception e) {
            log.error("Failed to upload stream to MinIO: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void downloadFile(String objectName, String destinationPath) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("MinIO is not available");
        }

        try {
            minioClient.downloadObject(
                    DownloadObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .filename(destinationPath)
                            .build()
            );

            log.info("Successfully downloaded file from MinIO: {} -> {}", objectName, destinationPath);

        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFile(String objectName) throws Exception {
        if (!isAvailable()) {
            log.warn("MinIO is not available. Skipping delete for: {}", objectName);
            return;
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );

            log.info("Successfully deleted file from MinIO: {}", objectName);

        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean fileExists(String objectName) throws Exception {
        if (!isAvailable()) {
            return false;
        }

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectName, int expirySeconds) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("MinIO is not available");
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );

            log.debug("Generated presigned URL for {}: {}", objectName, url);
            return url;

        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean isAvailable() {
        return minioClient != null && minioConfig.getEnabled();
    }

    /**
     * Upload entire backup directory to MinIO
     *
     * @param backupDirPath Local backup directory path
     * @param backupId      Backup ID for organizing in cloud
     * @return Cloud URL prefix
     * @throws Exception if upload fails
     */
    public String uploadBackupDirectory(String backupDirPath, String backupId) throws Exception {
        if (!isAvailable()) {
            log.warn("MinIO is not available. Skipping directory upload: {}", backupDirPath);
            return null;
        }

        try {
            java.io.File directory = new java.io.File(backupDirPath);
            java.io.File[] files = directory.listFiles();

            if (files == null || files.length == 0) {
                log.warn("No files found in backup directory: {}", backupDirPath);
                return null;
            }

            String cloudPrefix = "backups/" + backupId + "/";
            int uploadedCount = 0;

            for (java.io.File file : files) {
                if (file.isFile()) {
                    String objectName = cloudPrefix + file.getName();
                    uploadFile(file.getAbsolutePath(), objectName);
                    uploadedCount++;
                }
            }

            log.info("Successfully uploaded {} files to MinIO under: {}", uploadedCount, cloudPrefix);
            return minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + cloudPrefix;

        } catch (Exception e) {
            log.error("Failed to upload backup directory: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Download entire backup directory from MinIO
     *
     * @param backupId          Backup ID
     * @param destinationDir    Local destination directory
     * @throws Exception if download fails
     */
    public void downloadBackupDirectory(String backupId, String destinationDir) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("MinIO is not available");
        }

        // This would require listing objects with prefix, which we can add if needed
        log.info("Downloading backup directory for backup ID: {}", backupId);
        // Implementation would iterate through objects with prefix "backups/{backupId}/"
        // and download each one
    }
}

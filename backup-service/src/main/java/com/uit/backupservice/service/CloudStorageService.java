package com.uit.backupservice.service;

import java.io.File;
import java.io.InputStream;

/**
 * Interface for cloud storage operations.
 * Implementations can use MinIO, AWS S3, Google Cloud Storage, etc.
 */
public interface CloudStorageService {

    /**
     * Upload a file to cloud storage
     *
     * @param localFilePath Path to the local file
     * @param objectName    Object name in cloud storage (key)
     * @return Cloud URL of the uploaded file
     * @throws Exception if upload fails
     */
    String uploadFile(String localFilePath, String objectName) throws Exception;

    /**
     * Upload a file from InputStream to cloud storage
     *
     * @param inputStream Input stream of the file
     * @param objectName  Object name in cloud storage (key)
     * @param contentType Content type of the file
     * @param size        Size of the file in bytes
     * @return Cloud URL of the uploaded file
     * @throws Exception if upload fails
     */
    String uploadFile(InputStream inputStream, String objectName, String contentType, long size) throws Exception;

    /**
     * Download a file from cloud storage
     *
     * @param objectName       Object name in cloud storage (key)
     * @param destinationPath  Local path where file will be saved
     * @throws Exception if download fails
     */
    void downloadFile(String objectName, String destinationPath) throws Exception;

    /**
     * Delete a file from cloud storage
     *
     * @param objectName Object name in cloud storage (key)
     * @throws Exception if deletion fails
     */
    void deleteFile(String objectName) throws Exception;

    /**
     * Check if a file exists in cloud storage
     *
     * @param objectName Object name in cloud storage (key)
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String objectName) throws Exception;

    /**
     * Get presigned URL for downloading a file
     *
     * @param objectName Object name in cloud storage (key)
     * @param expirySeconds Expiry time in seconds
     * @return Presigned URL
     * @throws Exception if generation fails
     */
    String getPresignedDownloadUrl(String objectName, int expirySeconds) throws Exception;

    /**
     * Initialize cloud storage (e.g., create bucket if not exists)
     *
     * @throws Exception if initialization fails
     */
    void initialize() throws Exception;

    /**
     * Check if cloud storage is enabled and available
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}

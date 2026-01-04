package com.uit.backupservice.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
@Slf4j
public class MinIOConfig {

    private Boolean enabled = true;
    private String endpoint = "http://fortressbank-minio:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String bucketName = "fortressbank-backups";
    private Boolean autoCreateBucket = true;

    @Bean
    public MinioClient minioClient() {
        if (!enabled) {
            log.warn("MinIO is disabled. Cloud backup feature will not be available.");
            return null;
        }

        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            log.info("MinIO client initialized successfully: endpoint={}, bucket={}", endpoint, bucketName);
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage(), e);
            return null;
        }
    }
}

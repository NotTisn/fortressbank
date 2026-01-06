package com.uit.backupservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "backup")
@Data
public class DatabaseConfig {

    private String backupDirectory = "/app/backups";
    private Integer retentionDays = 30;
    private Boolean compressionEnabled = true;
    private Boolean encryptionEnabled = false;
    private String encryptionKey;
    private String scheduleCron = "0 2 * * *"; // 2 AM daily

    private Map<String, DatabaseInfo> databases = new HashMap<>();

    @Data
    public static class DatabaseInfo {
        private String containerName;
        private String databaseName;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Integer priority; // 1 = highest priority (critical)
    }

    public void initDefaultDatabases() {
        if (databases.isEmpty()) {
            // User Service Database
            DatabaseInfo userDb = new DatabaseInfo();
            userDb.setContainerName("user-service-db");
            userDb.setDatabaseName("userdb");
            userDb.setHost("user-service-db");
            userDb.setPort(5432);
            userDb.setUsername("postgres");
            userDb.setPassword("123456");
            userDb.setPriority(1);
            databases.put("user-service", userDb);

            // Account Service Database
            DatabaseInfo accountDb = new DatabaseInfo();
            accountDb.setContainerName("account-service-db");
            accountDb.setDatabaseName("accountdb");
            accountDb.setHost("account-service-db");
            accountDb.setPort(5432);
            accountDb.setUsername("postgres");
            accountDb.setPassword("123456");
            accountDb.setPriority(1);
            databases.put("account-service", accountDb);

            // Transaction Service Database
            DatabaseInfo transactionDb = new DatabaseInfo();
            transactionDb.setContainerName("transaction-service-db");
            transactionDb.setDatabaseName("transactiondb");
            transactionDb.setHost("transaction-service-db");
            transactionDb.setPort(5432);
            transactionDb.setUsername("postgres");
            transactionDb.setPassword("123456");
            transactionDb.setPriority(1);
            databases.put("transaction-service", transactionDb);

            // Audit Service Database
            DatabaseInfo auditDb = new DatabaseInfo();
            auditDb.setContainerName("audit-service-db");
            auditDb.setDatabaseName("auditdb");
            auditDb.setHost("audit-service-db");
            auditDb.setPort(5432);
            auditDb.setUsername("postgres");
            auditDb.setPassword("123456");
            auditDb.setPriority(2);
            databases.put("audit-service", auditDb);

            // Notification Service Database
            DatabaseInfo notificationDb = new DatabaseInfo();
            notificationDb.setContainerName("notification-service-db");
            notificationDb.setDatabaseName("notificationdb");
            notificationDb.setHost("notification-service-db");
            notificationDb.setPort(5432);
            notificationDb.setUsername("postgres");
            notificationDb.setPassword("123456");
            notificationDb.setPriority(3);
            databases.put("notification-service", notificationDb);
        }
    }
}

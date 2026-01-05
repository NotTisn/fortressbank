package com.uit.backupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.uit.backupservice", "com.uit.sharedkernel"})
@EnableDiscoveryClient
@EnableScheduling
@ConfigurationPropertiesScan
public class BackupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackupServiceApplication.class, args);
    }
}

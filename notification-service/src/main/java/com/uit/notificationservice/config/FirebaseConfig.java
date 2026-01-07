package com.uit.notificationservice.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class FirebaseConfig {
    
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    
    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;
    
    @Bean
    FirebaseMessaging firebaseMessaging() throws IOException {
        if (!firebaseEnabled) {
            log.warn("Firebase is disabled. Push notifications will not work.");
            return null;
        }
        
        ClassPathResource resource = new ClassPathResource("fortress-bank-230b4-firebase-adminsdk-fbsvc-ade09ee686.json");
        if (!resource.exists()) {
            log.warn("Firebase credentials file not found. Push notifications will not work.");
            return null;
        }
        
        GoogleCredentials googleCredentials = GoogleCredentials.fromStream(resource.getInputStream());

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(googleCredentials)
                .build();

        if(FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
        return FirebaseMessaging.getInstance();
    }
}

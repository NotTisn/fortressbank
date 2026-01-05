package com.uit.riskengine.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserRiskProfileClient {

    private final WebClient.Builder webClientBuilder;

    @SuppressWarnings("unchecked")
    public UserRiskProfileData getUserRiskProfile(String userId) {
        try {
            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri("http://user-service:4000/users/{userId}/risk-profile", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || response.get("data") == null) {
                log.warn("No risk profile found for user: {}", userId);
                return new UserRiskProfileData();
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            
            UserRiskProfileData profile = new UserRiskProfileData();
            profile.setKnownDevices((List<String>) data.get("knownDevices"));
            profile.setKnownLocations((List<String>) data.get("knownLocations"));
            profile.setKnownPayees((List<String>) data.get("knownPayees"));
            
            return profile;
            
        } catch (Exception e) {
            log.error("Failed to fetch risk profile for user {}: {}", userId, e.getMessage());
            // Fail-safe: return empty profile rather than blocking transaction
            return new UserRiskProfileData();
        }
    }

    /**
     * DTO for user risk profile data
     */
    @lombok.Data
    public static class UserRiskProfileData {
        private List<String> knownDevices = List.of();
        private List<String> knownLocations = List.of();
        private List<String> knownPayees = List.of();
    }
}

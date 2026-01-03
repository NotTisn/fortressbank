package com.uit.transactionservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test configuration that provides a mock JwtDecoder.
 * 
 * This avoids the need for Keycloak to be running during tests.
 * The mock decoder returns a valid-looking JWT for any token string.
 * 
 * For security tests that need to verify actual JWT validation,
 * use integration tests with TestContainers or a real Keycloak instance.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        // Return a mock JwtDecoder that accepts any token and returns a dummy JWT
        return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject("test-user-id")
                .issuer("http://localhost:8888/realms/fortressbank-realm")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of("user")))
                .claim("preferred_username", "testuser")
                .build();
    }
}

package com.uit.accountservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test Security Configuration to override production security settings.
 * Provides mock JwtDecoder for testing without requiring Keycloak.
 * 
 * Note: ParseUserInfoFilter was removed in security/penetration-tests branch.
 * Controllers now use @AuthenticationPrincipal Jwt jwt directly.
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * Provides a mock JwtDecoder that returns valid test JWTs.
     * This is used by Spring Security OAuth2 Resource Server.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        
        // Default mock JWT for tests
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "test-user-id")
                .claim("preferred_username", "testuser")
                .claim("email", "test@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        
        when(decoder.decode(anyString())).thenReturn(mockJwt);
        
        return decoder;
    }
}

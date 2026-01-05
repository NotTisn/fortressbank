package com.uit.accountservice.config;

import com.uit.accountservice.security.ParseUserInfoFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * Disables ParseUserInfoFilter and provides mock JwtDecoder for testing.
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * Mock ParseUserInfoFilter to disable it in tests.
     * Filter will be present but won't execute real logic.
     */
    @Bean
    @Primary
    public ParseUserInfoFilter parseUserInfoFilter() {
        // Return a no-op filter that doesn't do anything
        return new ParseUserInfoFilter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, 
                               jakarta.servlet.ServletResponse response, 
                               jakarta.servlet.FilterChain chain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                // Pass through without authentication check
                chain.doFilter(request, response);
            }
        };
    }

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

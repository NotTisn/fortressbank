package com.uit.transactionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JWT Configuration for Transaction Service.
 * 
 * Provides JwtDecoder bean for validating JWT tokens.
 * 
 * Configuration options (in order of precedence):
 * 1. jwt.jwk-set-uri + jwt.expected-issuer: Explicit key location and expected issuer
 *    - Use when JWKS is fetched from different URL than issuer (e.g., Docker internal)
 * 2. jwt.issuer-uri / spring.security.oauth2.resourceserver.jwt.issuer-uri:
 *    - Uses issuer URI for both JWKS discovery and issuer validation
 * 
 * SECURITY FIX (2024-12/2026-01):
 * - Added support for split jwk-set-uri and expected-issuer configuration
 * - This allows fetching keys from Docker-internal URL while validating tokens 
 *   with external issuer (e.g., localhost:8888)
 */
@Configuration
public class JwtConfig {

    @Value("${jwt.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri:}}")
    private String issuerUri;

    @Value("${jwt.jwk-set-uri:${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}}")
    private String jwkSetUri;

    @Value("${jwt.expected-issuer:}")
    private String expectedIssuer;

    /**
     * JwtDecoder bean for validating JWT tokens.
     * 
     * Uses Nimbus JOSE + JWT library (included in spring-security-oauth2-jose).
     * 
     * If jwt.jwk-set-uri is configured, uses explicit key fetching with custom issuer validation.
     * Otherwise, falls back to auto-discovery from issuer-uri.
     * 
     * @return JwtDecoder instance
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Option 1: Explicit JWK Set URI with separate expected issuer
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            
            if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new JwtIssuerValidator(expectedIssuer)
                ));
            } else {
                decoder.setJwtValidator(new JwtTimestampValidator());
            }
            
            return decoder;
        }
        
        // Option 2: Auto-discovery from issuer URI
        if (issuerUri != null && !issuerUri.isEmpty()) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        
        throw new IllegalStateException(
            "JWT configuration missing. Set either:\n" +
            "  - jwt.jwk-set-uri (with optional jwt.expected-issuer), OR\n" +
            "  - jwt.issuer-uri / spring.security.oauth2.resourceserver.jwt.issuer-uri"
        );
    }
}

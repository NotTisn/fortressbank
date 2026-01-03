package com.uit.accountservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

/**
 * JWT Configuration for Account Service.
 * 
 * Provides JwtDecoder bean for validating JWT tokens in both REST and SOAP requests.
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
     * Note: This bean is disabled in 'test' profile - tests use @MockBean or TestSecurityConfig.
     * 
     * @return JwtDecoder instance
     */
    @Bean
    @Profile("!test")
    public JwtDecoder jwtDecoder() {
        // Option 1: Explicit JWK Set URI with separate expected issuer
        // Useful for Docker environments where JWKS is fetched internally
        // but tokens have external issuer (e.g., localhost:8888)
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            
            // If expected issuer is configured, validate against it
            // Otherwise, skip issuer validation (signature still verified)
            if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new JwtIssuerValidator(expectedIssuer)
                ));
            } else {
                // Only validate timestamp, signature is always verified
                decoder.setJwtValidator(new JwtTimestampValidator());
            }
            
            return decoder;
        }
        
        // Option 2: Auto-discovery from issuer URI
        // JwtDecoders.fromIssuerLocation() automatically:
        // - Fetches /.well-known/openid-configuration from issuer
        // - Extracts jwks_uri from the config
        // - Configures signature verification with correct algorithms
        // - Validates issuer claim matches
        // - Caches public keys
        if (issuerUri != null && !issuerUri.isEmpty()) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        
        // Fail-fast: config is missing - do NOT silently accept unverified tokens
        throw new IllegalStateException(
            "JWT configuration missing. Set either:\n" +
            "  - jwt.jwk-set-uri (with optional jwt.expected-issuer), OR\n" +
            "  - jwt.issuer-uri / spring.security.oauth2.resourceserver.jwt.issuer-uri"
        );
    }
}

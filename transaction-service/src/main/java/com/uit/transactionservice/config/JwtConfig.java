package com.uit.transactionservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JWT Configuration for Transaction Service.
 * 
 * Provides JwtDecoder bean for validating JWT tokens.
 * 
 * Configuration options (in order of precedence):
 * 1. jwt.enabled=false: Disables JWT validation entirely (for testing without Keycloak)
 * 2. jwt.jwk-set-uri + jwt.expected-issuer: Explicit key location and expected issuer
 *    - Use when JWKS is fetched from different URL than issuer (e.g., Docker internal)
 * 3. jwt.issuer-uri / spring.security.oauth2.resourceserver.jwt.issuer-uri:
 *    - Uses issuer URI for both JWKS discovery and issuer validation
 * 
 * For tests: Set jwt.enabled=false in application-test.yml to use a permissive mock decoder.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${jwt.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri:}}")
    private String issuerUri;

    @Value("${jwt.jwk-set-uri:${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}}")
    private String jwkSetUri;

    @Value("${jwt.expected-issuer:}")
    private String expectedIssuer;

    /**
     * Production JwtDecoder - connects to real Keycloak/OIDC provider.
     * Only created when jwt.enabled=true (default).
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        // Option 1: Explicit JWK Set URI with separate expected issuer
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            log.info("Configuring JWT decoder with explicit JWK Set URI: {}", jwkSetUri);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            
            if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
                log.info("Validating JWT issuer against: {}", expectedIssuer);
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
            log.info("Configuring JWT decoder with issuer discovery: {}", issuerUri);
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        
        throw new IllegalStateException(
            "JWT configuration missing. Set either:\n" +
            "  - jwt.jwk-set-uri (with optional jwt.expected-issuer), OR\n" +
            "  - jwt.issuer-uri / spring.security.oauth2.resourceserver.jwt.issuer-uri\n" +
            "  - OR set jwt.enabled=false for testing without Keycloak"
        );
    }

    /**
     * Test/Development JwtDecoder - used when jwt.enabled=false.
     * Returns a permissive decoder that accepts any well-formed token.
     * 
     * WARNING: Never use jwt.enabled=false in production!
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.enabled", havingValue = "false")
    public JwtDecoder testJwtDecoder() {
        log.warn("⚠️  JWT validation DISABLED - using permissive decoder. DO NOT USE IN PRODUCTION!");
        return token -> {
            // Return a minimal valid JWT for testing purposes
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .issuer("test-issuer")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .claim("realm_access", Map.of("roles", List.of("user")))
                    .build();
        };
    }
}

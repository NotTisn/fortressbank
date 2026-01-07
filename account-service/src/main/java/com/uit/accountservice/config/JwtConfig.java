package com.uit.accountservice.config;

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
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JWT Configuration for Account Service.
 * 
 * Provides JwtDecoder bean for validating JWT tokens in both REST and SOAP requests.
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
        try {
            return createJwtDecoder();
        } catch (Exception e) {
            // Check if this is a connection failure (infrastructure not running)
            if (isConnectionFailure(e)) {
                String infraUrl = jwkSetUri != null && !jwkSetUri.isEmpty() ? jwkSetUri : issuerUri;
                throw new IllegalStateException(
                    "\n\n" +
                    "╔══════════════════════════════════════════════════════════════════════╗\n" +
                    "║  ❌ KEYCLOAK NOT REACHABLE                                            ║\n" +
                    "╠══════════════════════════════════════════════════════════════════════╣\n" +
                    "║  Cannot connect to: " + padRight(infraUrl, 47) + "║\n" +
                    "║                                                                      ║\n" +
                    "║  FIX OPTIONS:                                                        ║\n" +
                    "║  1. Start infrastructure first:                                      ║\n" +
                    "║     cd fortressbank/infrastructure                                   ║\n" +
                    "║     ./dev.bat -infra                                                 ║\n" +
                    "║                                                                      ║\n" +
                    "║  2. Or use full Docker mode:                                         ║\n" +
                    "║     cd fortressbank                                                  ║\n" +
                    "║     docker compose up -d                                             ║\n" +
                    "║                                                                      ║\n" +
                    "║  3. Or disable JWT for testing (NOT for production!):               ║\n" +
                    "║     Set environment variable: JWT_ENABLED=false                      ║\n" +
                    "╚══════════════════════════════════════════════════════════════════════╝\n",
                    e
                );
            }
            throw e;
        }
    }
    
    private JwtDecoder createJwtDecoder() {
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
    
    /**
     * Check if the exception is caused by infrastructure not being reachable.
     */
    private boolean isConnectionFailure(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException || 
                cause instanceof ResourceAccessException ||
                (cause.getMessage() != null && cause.getMessage().contains("Connection refused"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    
    /**
     * Pad string to fixed width for error message box alignment.
     */
    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}


package com.uit.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JWT Configuration for User Service.
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
        try {
            return createJwtDecoder();
        } catch (Exception e) {
            // Check if this is a connection failure (Keycloak not running)
            if (isConnectionFailure(e)) {
                String uri = (jwkSetUri != null && !jwkSetUri.isEmpty()) ? jwkSetUri : issuerUri;
                int boxWidth = 70;
                String border = "═".repeat(boxWidth);
                String msg = String.format(
                    "\n╔%s╗\n" +
                    "║  ❌ KEYCLOAK NOT REACHABLE %s║\n" +
                    "╠%s╣\n" +
                    "║  %s║\n" +
                    "║  %s║\n" +
                    "║  %s║\n" +
                    "║  %s║\n" +
                    "║  %s║\n" +
                    "╚%s╝\n",
                    border,
                    padRight("", boxWidth - 29),
                    border,
                    padRight("Cannot connect to: " + uri, boxWidth - 2),
                    padRight("", boxWidth - 2),
                    padRight("FIX OPTIONS:", boxWidth - 2),
                    padRight("  1. Start infrastructure first: ./dev.bat -infra", boxWidth - 2),
                    padRight("  2. Or use full Docker mode: docker compose up -d", boxWidth - 2),
                    border
                );
                log.error(msg);
                throw new IllegalStateException("Keycloak not reachable at: " + uri + 
                    ". Start infrastructure with: ./dev.bat -infra", e);
            }
            throw new IllegalStateException("Failed to configure JWT decoder", e);
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
            
            // Test connection by fetching keys
            try {
                decoder.decode("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0In0.test");
            } catch (Exception ignored) {
                // Expected to fail - we just want to trigger key fetch
            }
            
            return decoder;
        }
        
        // Option 2: Auto-discovery from issuer URI
        if (issuerUri != null && !issuerUri.isEmpty()) {
            log.info("Configuring JWT decoder with issuer discovery: {}", issuerUri);
            return org.springframework.security.oauth2.jwt.JwtDecoders.fromIssuerLocation(issuerUri);
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
    
    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}


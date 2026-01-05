package com.uit.accountservice.config;

import com.uit.accountservice.security.RoleCheckInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Security Configuration for Account Service.
 * 
 * SECURITY FIX (2024-12):
 * - Replaced ParseUserInfoFilter (Base64 decode only) with proper JWT validation
 * - Uses Spring Security OAuth2 Resource Server with JwtDecoder
 * - JWT signature is now verified via Keycloak's JWKS endpoint
 * - Defense-in-depth: internal endpoints require network-level protection
 * 
 * @see JwtConfig for JwtDecoder bean configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig implements WebMvcConfigurer {
    
    private final RoleCheckInterceptor roleCheckInterceptor;
    
    public SecurityConfig(RoleCheckInterceptor roleCheckInterceptor) {
        this.roleCheckInterceptor = roleCheckInterceptor;
    }
    
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // SOAP endpoints - validated by SoapSecurityInterceptor
                .requestMatchers("/ws/**").permitAll()
                // Internal endpoints - should only be accessible via service mesh
                // SECURITY NOTE: In production, these should be protected by network policies
                .requestMatchers("/accounts/internal/**").permitAll()
                .requestMatchers("/cards/internal/**").permitAll()
                // Public endpoints - no authentication required
                .requestMatchers("/accounts/public/**").permitAll()
                // Actuator health check
                .requestMatchers("/actuator/**").permitAll()
                // All other endpoints require valid JWT (defense-in-depth, Kong also validates)
                .anyRequest().authenticated()
            )
            // Use OAuth2 Resource Server with JWT validation
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            ));
        return http.build();
    }
    
    /**
     * JWT Authentication Converter that extracts Keycloak realm roles.
     * Converts realm_access.roles claim to Spring Security authorities.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
    
    /**
     * Keycloak Role Converter - extracts roles from realm_access claim.
     * 
     * Keycloak JWT structure:
     * {
     *   "realm_access": {
     *     "roles": ["admin", "user"]
     *   }
     * }
     */
    public static class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
            
            if (realmAccess == null || realmAccess.isEmpty()) {
                return List.of();
            }
            
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) {
                return List.of();
            }
            
            // Convert to Spring Security authorities with ROLE_ prefix
            // NOTE: Do NOT uppercase - Spring Security's hasRole('admin') expects 'ROLE_admin' not 'ROLE_ADMIN'
            return roles.stream()
                    .map(roleName -> "ROLE_" + roleName.toUpperCase())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
    }
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(roleCheckInterceptor)
                .excludePathPatterns("/accounts/internal/**", "/accounts/public/**", "/actuator/**", "/ws/**");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
package com.uit.userservice.keycloak;

import com.uit.userservice.config.KeycloakProperties;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.userservice.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakClient {

    private final KeycloakProperties properties;
    private final RestTemplate restTemplate;

    private String tokenEndpoint() {
        return properties.getAuthServerUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";
    }

    private String logoutEndpoint() {
        return properties.getAuthServerUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/logout";
    }

    private String usersEndpoint() {
        return properties.getAuthServerUrl()
                + "/admin/realms/" + properties.getRealm()
                + "/users";
    }

    private String userPasswordEndpoint(String userId) {
        return usersEndpoint() + "/" + userId + "/reset-password";
    }

    private String userResourceEndpoint(String userId) {
        return usersEndpoint() + "/" + userId;
    }
    // =============== TOKEN FLOWS ===============

    public TokenResponse loginWithPassword(String username, String password) {
        return callTokenEndpoint(Map.of(
                "grant_type", "password",
                "username", username,
                "password", password
        ));
    }

    public TokenResponse refreshToken(String refreshToken) {
        return callTokenEndpoint(Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken
        ));
    }

    private TokenResponse callTokenEndpoint(Map<String, String> extraParams) {
        String url = tokenEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("scope", "openid");
        extraParams.forEach(form::add);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(url, entity, TokenResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(ErrorCode.USER_CREATION_FAILED, "Keycloak token request failed");
            }
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak token error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.FORBIDDEN, "Authentication failed");
        }
    }

    public void logout(String refreshToken) {
        String url = logoutEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("refresh_token", refreshToken);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(form, headers), Void.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak logout error: {}", ex.getResponseBodyAsString());
            // không throw, logout lỗi cũng không cần fail request
        }
    }

    // =============== ADMIN TOKEN ===============

    private String getAdminAccessToken() {
        String url = tokenEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(form, headers), TokenResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(ErrorCode.USER_CREATION_FAILED, "Cannot get admin token from Keycloak");
            }

            return response.getBody().accessToken();
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak admin token error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.USER_CREATION_FAILED, "Cannot get admin token from Keycloak");
        }
    }

    // =============== ADMIN USER APIS ===============

    /**
     * Create user in Keycloak with phone number attribute.
     * Phone number will be stored in user attributes and can be mapped to JWT token.
     * 
     * @param username Username for login
     * @param email User's email
     * @param fullName Full name of user
     * @param password User's password
     * @param phoneNumber User's phone number (will be added to JWT token)
     * @return Keycloak user ID
     */
    public String createUser(String username, String email, String fullName, String password, String phoneNumber) {
        String adminToken = getAdminAccessToken();

        String url = usersEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build user attributes with phoneNumber
        Map<String, Object> attributes = Map.of(
                "phoneNumber", List.of(phoneNumber) // Keycloak attributes are arrays
        );

        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "enabled", true,
                "emailVerified", false,
                "firstName", fullName, // đơn giản dùng fullName làm firstName
                "attributes", attributes, // Add custom attributes
                "credentials", new Object[]{
                        Map.of(
                                "type", "password",
                                "value", password,
                                "temporary", false
                        )
                }
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(url, entity, Void.class);

            if (response.getStatusCode() != HttpStatus.CREATED) {
                throw new AppException(ErrorCode.USER_CREATION_FAILED);
            }

            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location == null) {
                throw new AppException(ErrorCode.USER_CREATION_FAILED);
            }

            URI uri = URI.create(location);
            String path = uri.getPath();
            String userId = path.substring(path.lastIndexOf('/') + 1);
            
            log.info("User created in Keycloak: {} with phoneNumber: {}", userId, phoneNumber);
            return userId;
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak create user error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.USER_CREATION_FAILED);
        }
    }

    /**
     * Legacy method for backward compatibility (without phoneNumber)
     */
    public String createUser(String username, String email, String fullName, String password) {
        return createUser(username, email, fullName, password, null);
    }

    public void resetPassword(String userId, String newPassword) {
        String adminToken = getAdminAccessToken();

        String url = userPasswordEndpoint(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak reset password error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.FORBIDDEN, "Cannot change password");
        }
    }

    /**
     * Dùng để verify oldPassword trong flow change-password
     */
    public void verifyPassword(String username, String oldPassword) {
        // nếu login thành công thì ok, nếu lỗi thì throw
        loginWithPassword(username, oldPassword);
    }

    // 1. Update User Status (Lock/Unlock)
    public void updateUserStatus(String userId, boolean isEnabled) {
        String adminToken = getAdminAccessToken();
        String url = userResourceEndpoint(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("enabled", isEnabled);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak update status error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    // 2. Get User Detail from Keycloak (để lấy real-time status)
    public Map<String, Object> getUserFromKeycloak(String userId) {
        String adminToken = getAdminAccessToken();
        String url = userResourceEndpoint(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak get user error: {}", ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    // 3. Create role if not exists
    public void createRoleIfNotExists(String roleName) {
        String adminToken = getAdminAccessToken();
        String rolesUrl = properties.getAuthServerUrl()
                + "/admin/realms/" + properties.getRealm()
                + "/roles";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        try {
            // Check if role exists
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            ResponseEntity<List> rolesResponse = restTemplate.exchange(
                    rolesUrl,
                    HttpMethod.GET,
                    getEntity,
                    List.class
            );

            List<Map<String, Object>> allRoles = (List<Map<String, Object>>) rolesResponse.getBody();
            if (allRoles != null) {
                boolean roleExists = allRoles.stream()
                        .anyMatch(role -> roleName.equals(role.get("name")));

                if (roleExists) {
                    log.info("Role '{}' already exists in Keycloak", roleName);
                    return;
                }
            }

            // Create role if not exists
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> roleData = Map.of(
                    "name", roleName,
                    "description", "Auto-created role: " + roleName
            );
            HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(roleData, headers);

            restTemplate.postForEntity(rolesUrl, createEntity, Void.class);
            log.info("Created role '{}' in Keycloak", roleName);

        } catch (HttpStatusCodeException ex) {
            log.error("Failed to create role '{}': {}", roleName, ex.getResponseBodyAsString());
        }
    }

    // 4. Assign realm roles to user
    public void assignRealmRolesToUser(String userId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return;
        }

        String adminToken = getAdminAccessToken();

        // Step 0: Ensure all roles exist (create if not)
        for (String roleName : roleNames) {
            createRoleIfNotExists(roleName);
        }

        // Step 1: Get available realm roles
        String availableRolesUrl = properties.getAuthServerUrl()
                + "/admin/realms/" + properties.getRealm()
                + "/roles";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // Get all available roles
            ResponseEntity<List> rolesResponse = restTemplate.exchange(
                    availableRolesUrl,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map<String, Object>> allRoles = (List<Map<String, Object>>) rolesResponse.getBody();
            if (allRoles == null) {
                log.warn("No roles available in Keycloak realm");
                return;
            }

            // Filter roles that match the requested roleNames
            List<Map<String, Object>> rolesToAssign = allRoles.stream()
                    .filter(role -> roleNames.contains(role.get("name")))
                    .map(role -> Map.of(
                            "id", role.get("id"),
                            "name", role.get("name")
                    ))
                    .toList();

            if (rolesToAssign.isEmpty()) {
                log.warn("None of the requested roles {} exist in Keycloak", roleNames);
                return;
            }

            // Step 2: Assign roles to user
            String assignRolesUrl = properties.getAuthServerUrl()
                    + "/admin/realms/" + properties.getRealm()
                    + "/users/" + userId + "/role-mappings/realm";

            HttpHeaders assignHeaders = new HttpHeaders();
            assignHeaders.setBearerAuth(adminToken);
            assignHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<List<Map<String, Object>>> assignEntity = new HttpEntity<>(rolesToAssign, assignHeaders);

            restTemplate.postForEntity(assignRolesUrl, assignEntity, Void.class);
            log.info("Assigned roles {} to user {}", roleNames, userId);

        } catch (HttpStatusCodeException ex) {
            log.error("Keycloak assign roles error: {}", ex.getResponseBodyAsString());
            // Don't throw exception, just log warning
            log.warn("Failed to assign roles {} to user {}", roleNames, userId);
        }
    }
}

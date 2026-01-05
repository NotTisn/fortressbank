package com.uit.sharedkernel.security;

import java.util.List;
import java.util.Map;

/**
 * Utility class for extracting user information from JWT tokens.
 * Works with Spring Security's Jwt object without requiring direct dependency.
 * 
 * This utility uses reflection to access Jwt methods, making it safe to use
 * in modules that don't have spring-security-oauth2-jose as dependency.
 */
public class JwtUtils {

    /**
     * Extract user ID from JWT token (subject claim).
     * Uses reflection to call jwt.getSubject() safely.
     * 
     * @param jwt JWT token object (org.springframework.security.oauth2.jwt.Jwt)
     * @return User ID or null if not present
     */
    public static String getUserId(Object jwt) {
        try {
            return (String) invokeMethod(jwt, "getSubject");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract username from JWT token (preferred_username claim).
     * 
     * @param jwt JWT token object
     * @return Username or null
     */
    public static String getUsername(Object jwt) {
        return getClaimAsString(jwt, "preferred_username");
    }

    /**
     * Extract email from JWT token.
     * 
     * @param jwt JWT token object
     * @return Email address or null
     */
    public static String getEmail(Object jwt) {
        return getClaimAsString(jwt, "email");
    }

    /**
     * Extract phone number from JWT token.
     * Tries multiple claim names: phone_number (Keycloak default) and phoneNumber (custom).
     * 
     * @param jwt JWT token object
     * @return Phone number in format +84xxxxxxxxx or null
     */
    public static String getPhoneNumber(Object jwt) {
        // Try phone_number first (Keycloak default with underscore)
        String phoneNumber = getClaimAsString(jwt, "phone_number");
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        // Fallback to phoneNumber (camelCase)
        return getClaimAsString(jwt, "phoneNumber");
    }

    /**
     * Extract full name from JWT token (name claim).
     * 
     * @param jwt JWT token object
     * @return Full name or null
     */
    public static String getFullName(Object jwt) {
        return getClaimAsString(jwt, "name");
    }

    /**
     * Check if user has a specific role.
     * Checks realm_access.roles claim.
     * 
     * @param jwt JWT token object
     * @param roleName Role name to check (e.g., "admin", "user")
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(Object jwt, String roleName) {
        try {
            Map<String, Object> realmAccess = getClaimAsMap(jwt, "realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<?> roles = (List<?>) realmAccess.get("roles");
                return roles != null && roles.contains(roleName);
            }
        } catch (Exception e) {
            // Log or handle exception if needed
        }
        return false;
    }

    /**
     * Get all claims from JWT token for debugging purposes.
     * 
     * @param jwt JWT token object
     * @return Map of all claims, or empty map if error
     */
    public static Map<String, Object> getAllClaims(Object jwt) {
        try {
            return (Map<String, Object>) invokeMethod(jwt, "getClaims");
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Generic method to extract a claim as String.
     * Uses reflection to call jwt.getClaimAsString(claimName).
     * 
     * @param jwt JWT token object
     * @param claimName Name of the claim to extract
     * @return Claim value as String, or null if not found/error
     */
    private static String getClaimAsString(Object jwt, String claimName) {
        try {
            // Fix: Call method with correct signature - getClaimAsString(String claimName)
            var method = jwt.getClass().getMethod("getClaimAsString", String.class);
            return (String) method.invoke(jwt, claimName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generic method to extract a claim as Map.
     * Uses reflection to call jwt.getClaimAsMap(claimName).
     * 
     * @param jwt JWT token object
     * @param claimName Name of the claim to extract
     * @return Claim value as Map, or null if not found/error
     */
    private static Map<String, Object> getClaimAsMap(Object jwt, String claimName) {
        try {
            // Fix: Call method with correct signature - getClaimAsMap(String claimName)
            var method = jwt.getClass().getMethod("getClaimAsMap", String.class);
            return (Map<String, Object>) method.invoke(jwt, claimName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper method to invoke a method on the JWT object using reflection.
     * This allows the class to work without directly importing Jwt class.
     * 
     * @param jwt JWT object
     * @param methodName Name of method to invoke
     * @param args Method arguments
     * @return Result of method invocation
     * @throws Exception If reflection fails
     */
    private static Object invokeMethod(Object jwt, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        var method = jwt.getClass().getMethod(methodName, paramTypes);
        return method.invoke(jwt, args);
    }

    /**
     * Overloaded version for methods with no parameters.
     */
    private static Object invokeMethod(Object jwt, String methodName) throws Exception {
        var method = jwt.getClass().getMethod(methodName);
        return method.invoke(jwt);
    }
}

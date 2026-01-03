package com.uit.accountservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Parses JWT token from Authorization header and extracts user info.
 * Sets userInfo as request attribute for controllers to use.
 * 
 * Note: This runs alongside Spring Security OAuth2 Resource Server.
 * OAuth2 Resource Server validates the JWT signature.
 * This filter extracts claims for backward compatibility with existing controllers.
 */
@Component
public class ParseUserInfoFilter implements Filter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ParseUserInfoFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip authentication for internal and public endpoints
        String requestPath = httpRequest.getRequestURI();
        if (requestPath.startsWith("/accounts/internal/") || requestPath.startsWith("/accounts/public/") || requestPath.startsWith("/cards/internal")) {
            chain.doFilter(request, response);
            return;
        }

        // If authentication already exists (e.g., from Spring Security OAuth2 Resource Server or tests),
        // just extract userInfo from the existing JWT token and continue
        var existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated() 
                && !"anonymousUser".equals(existingAuth.getPrincipal())) {
            // Auth already set by OAuth2 Resource Server - try to extract userInfo from Authorization header
            // This provides backward compatibility for controllers that use request.getAttribute("userInfo")
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7).trim();
                    String[] chunks = token.split("\\.");
                    if (chunks.length >= 2) {
                        String payload = new String(Base64.getUrlDecoder().decode(chunks[1]), StandardCharsets.UTF_8);
                        Map<String, Object> userInfo = objectMapper.readValue(payload, Map.class);
                        httpRequest.setAttribute("userInfo", userInfo);
                    }
                } catch (Exception e) {
                    log.debug("Could not extract userInfo from existing auth: {}", e.getMessage());
                }
            }
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        Map<String, Object> userInfo = null;

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // 1. Lấy token và cắt khoảng trắng thừa
                String token = authHeader.substring(7).trim();

                // LOG ĐỂ DEBUG: In ra 10 ký tự đầu xem có bị dính dấu nháy " không
                log.info("Processing Token: {}...", token.substring(0, Math.min(token.length(), 10)));

                String[] chunks = token.split("\\.");

                if (chunks.length >= 2) {
                    // 2. Decode phần Payload (Chunk 1)
                    Base64.Decoder decoder = Base64.getUrlDecoder();
                    // Lưu ý: Dùng StandardCharsets.UTF_8 để tránh lỗi encoding
                    String payload = new String(decoder.decode(chunks[1]), StandardCharsets.UTF_8);

                    // LOG ĐỂ DEBUG: Xem payload giải mã ra cái gì
                    log.info("Decoded Payload: {}", payload);

                    // 3. Parse JSON
                    userInfo = objectMapper.readValue(payload, Map.class);
                } else {
                    log.error("Invalid JWT Token format: Not enough chunks (found {})", chunks.length);
                }
            }

            if (userInfo != null) {
                httpRequest.setAttribute("userInfo", userInfo);
                UserInfoAuthentication authentication = new UserInfoAuthentication(userInfo);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
            } else {
                log.warn("Unauthorized: Token is missing or invalid structure");
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Unauthorized: Invalid Token\"}");
            }

        } catch (Exception e) {
            // In toàn bộ lỗi ra console để bạn xem
            log.error("CRITICAL ERROR parsing token: ", e);

            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.setContentType("application/json");
            // Trả về lỗi chi tiết hơn cho Postman
            httpResponse.getWriter().write("{\"error\":\"Bad Request: " + e.getMessage() + "\"}");
        }
        // Note: Do NOT clear SecurityContext here - let Spring Security handle the lifecycle
    }
}
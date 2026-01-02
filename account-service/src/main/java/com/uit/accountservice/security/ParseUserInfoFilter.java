package com.uit.accountservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * DEPRECATED: This filter is no longer used.
 * JWT authentication is now handled by Spring Security OAuth2 Resource Server.
 * 
 * @see com.uit.accountservice.config.SecurityConfig
 * @see com.uit.accountservice.config.JwtConfig
 * 
 * This class is kept for reference but disabled by removing @Component.
 * Delete this file in a future cleanup.
 */
// @Component - DISABLED: Replaced by OAuth2 Resource Server JWT authentication
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
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
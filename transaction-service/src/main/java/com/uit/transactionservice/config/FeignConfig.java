package com.uit.transactionservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign Client Configuration
 * Configures request interceptors and other Feign settings
 */
@Configuration
@Slf4j
public class FeignConfig {

    /**
     * Request interceptor to forward JWT token from incoming request
     * to outgoing Feign client requests
     *
     * This ensures that inter-service calls maintain the same security context
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes != null) {
                    String authHeader = attributes.getRequest().getHeader("Authorization");
                    if (authHeader != null) {
                        // Forwarding JWT token is disabled to avoid 401 on internal endpoints
                        // template.header("Authorization", authHeader);
                        log.debug("Authorization header forwarding is disabled for {} {}",
                            template.method(), template.url());
                    }
                }
            }
        };
    }
}

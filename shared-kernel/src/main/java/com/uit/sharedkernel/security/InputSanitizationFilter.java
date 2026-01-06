package com.uit.sharedkernel.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to sanitize input against XSS using Jsoup.
 * Acts as a "Safety Net" middleware for URL parameters and headers.
 */
@Component
@RequiredArgsConstructor
public class InputSanitizationFilter implements Filter {

    private final XssSanitizer xssSanitizer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new SanitizedRequestWrapper((HttpServletRequest) request, xssSanitizer), response);
    }

    private static class SanitizedRequestWrapper extends HttpServletRequestWrapper {

        private final XssSanitizer xssSanitizer;

        public SanitizedRequestWrapper(HttpServletRequest servletRequest, XssSanitizer xssSanitizer) {
            super(servletRequest);
            this.xssSanitizer = xssSanitizer;
        }

        @Override
        public String[] getParameterValues(String parameter) {
            String[] values = super.getParameterValues(parameter);
            if (values == null) {
                return null;
            }
            int count = values.length;
            String[] encodedValues = new String[count];
            for (int i = 0; i < count; i++) {
                encodedValues[i] = sanitize(values[i]);
            }
            return encodedValues;
        }

        @Override
        public String getParameter(String parameter) {
            String value = super.getParameter(parameter);
            return sanitize(value);
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return sanitize(value);
        }

        private String sanitize(String value) {
            if (value != null) {
                return xssSanitizer.sanitize(value);
            }
            return value;
        }
    }
}

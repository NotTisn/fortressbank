package com.uit.transactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.uit.sharedkernel.security.HtmlSanitizationDeserializer;
import com.uit.sharedkernel.security.XssSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit Test focused on XSS Sanitization Logic.
 * Verifies that JSON deserialization correctly strips malicious HTML/Script tags.
 */
public class TransactionXssTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        
        // Manual setup of the sanitizer module (simulating what Spring Boot does)
        XssSanitizer xssSanitizer = new XssSanitizer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new HtmlSanitizationDeserializer(xssSanitizer));
        
        objectMapper.registerModule(module);
    }

    @Test
    public void testXssSanitization_ScriptTag() throws Exception {
        String json = "{\"description\": \"<script>alert('hacked')</script>Payment\"}";
        
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        
        System.out.println("Input JSON: " + json);
        System.out.println("Output DTO: " + dto.description);
        
        assertFalse(dto.description.contains("<script>"), "Should not contain script tag");
        assertEquals("Payment", dto.description.trim(), "Should only contain safe text");
    }

    @Test
    public void testXssSanitization_ImgOnError() throws Exception {
        String json = "{\"description\": \"<img src=x onerror=alert(1)>Hack\"}";
        
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        
        System.out.println("Output: " + dto.description);
        assertEquals("Hack", dto.description.trim(), "Should remove img tag and attributes");
    }

    @Test
    public void testNormalText_ShouldRemainUnchanged() throws Exception {
        String json = "{\"description\": \"Normal payment for rent\"}";
        
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        
        assertEquals("Normal payment for rent", dto.description);
    }

    // Helper DTO
    static class TestDto {
        public String description;
    }
}

package com.uit.transactionservice.controller;

import com.uit.transactionservice.dto.SepayWebhookDto;
import com.uit.transactionservice.service.TransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/webhooks/sepay")
@RequiredArgsConstructor
@Slf4j
public class SepayWebhookController {

    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<String> handleSepayWebhook(@RequestBody SepayWebhookDto webhookDto) {
        try {
            log.info("Received SePay Webhook: {}", objectMapper.writeValueAsString(webhookDto));
        } catch (JsonProcessingException e) {
            log.error("Failed to log webhook payload", e);
        }

        if (!"in".equalsIgnoreCase(webhookDto.getTransferType())) {
            log.info("Ignoring outgoing transfer webhook from SePay: {}", webhookDto.getCode());
            return ResponseEntity.ok("Ignored outgoing transfer");
        }

        String content = webhookDto.getContent();
        String accountNumber = extractAccountId(content);

        if (accountNumber == null) {
            log.error("Failed to extract Account ID from content: {}", content);
            return ResponseEntity.badRequest().body("Invalid content format. Expected FB<AccountId>");
        }

        try {
            transactionService.handleSepayTopup(webhookDto, accountNumber);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing SePay webhook", e);
            return ResponseEntity.internalServerError().body("Error processing webhook: " + e.getMessage());
        }
    }

    private String extractAccountId(String content) {
        if (content == null) return null;
        
        // First try to match the full UUID format: FB<uuid-with-hyphens>
        Pattern fullPattern = Pattern.compile("FB([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");
        Matcher fullMatcher = fullPattern.matcher(content);
        
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        
        // Fallback: Try to reconstruct UUID from separated parts
        // Pattern: FB<part1> <part2> <part3> <part4> <part5>
        // Example: FBa1b2c3d4 e5f6 7890 1234 567890abcdef
        Pattern separatedPattern = Pattern.compile("FB([a-f0-9]{8})\\s+([a-f0-9]{4})\\s+([a-f0-9]{4})\\s+([a-f0-9]{4})\\s+([a-f0-9]{12})");
        Matcher separatedMatcher = separatedPattern.matcher(content);
        
        if (separatedMatcher.find()) {
            // Reconstruct the UUID with hyphens
            String uuid = String.format("%s-%s-%s-%s-%s",
                    separatedMatcher.group(1),
                    separatedMatcher.group(2),
                    separatedMatcher.group(3),
                    separatedMatcher.group(4),
                    separatedMatcher.group(5));
            log.info("Reconstructed UUID from separated parts: {}", uuid);
            return uuid;
        }
        
        log.warn("Failed to extract Account ID from content. Expected format: FB<uuid> or FB<part1> <part2> ... Content: {}", content);
        return null;
    }
}

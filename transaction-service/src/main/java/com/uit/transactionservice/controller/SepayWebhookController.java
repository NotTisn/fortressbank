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
        
        Pattern pattern = Pattern.compile("FB([a-zA-Z0-9-]{36})");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}

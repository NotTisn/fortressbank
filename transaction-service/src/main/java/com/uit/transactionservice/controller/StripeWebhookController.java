package com.uit.transactionservice.controller;

import com.google.gson.JsonObject;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Transfer;
import com.stripe.model.Charge;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.uit.transactionservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook controller for receiving Stripe events
 * Handles: payment.created (Connected Account receives funds), transfer.reversed, transfer.failed
 */
@RestController
@RequestMapping("transactions/public/api/webhook/stripe")
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final TransactionService transactionService;

    /**
     * Handle Stripe webhook events
     * 
     * @param payload Raw request body
     * @param sigHeader Stripe-Signature header
     * @return ResponseEntity
     */
    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("Received Stripe webhook - Signature: {}", sigHeader);

        try {
            // Validate webhook signature to prevent fraud
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            
            String accountId = event.getAccount(); // null = Platform event, non-null = Connected Account event
            
            log.info("Webhook validated - Event Type: {} - ID: {} - Account: {}", 
                    event.getType(), event.getId(), accountId);

            // Handle based on event type
            switch (event.getType()) {
                case "transfer.created":
                    // Platform event - just log, actual confirmation comes from payment.created
                    log.info("Platform transfer created - EventID: {}", event.getId());
                    break;
                    
                case "payment.created":
                    // Connected Account received funds - THIS CONFIRMS USER B GOT MONEY
                    if (accountId != null) {
                        handlePaymentCreated(event, accountId);
                    }
                    break;
                    
                case "transfer.reversed":
                    handleTransferReversed(event);
                    break;
                    
                case "transfer.failed":
                    handleTransferFailed(event);
                    break;
                    
                default:
                    log.info("Unhandled webhook event type: {}", event.getType());
            }

            return ResponseEntity.ok("Webhook processed successfully");

        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature - Possible fraud attempt", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");

        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
            // Return 500 so Stripe will retry
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    /**
     * Handle payment.created event from Connected Account
     * This event CONFIRMS that user B has RECEIVED the funds
     */
    private void handlePaymentCreated(Event event, String accountId) {
        try {
            String webhookIdempotencyKey = event.getId();
            
            log.info("📥 Processing payment.created - EventID: {} - API Version: {}", webhookIdempotencyKey, event.getApiVersion());
            
            // Deserialize the nested object inside the event
            com.stripe.model.EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            com.stripe.model.StripeObject stripeObject = null;
            
            if (dataObjectDeserializer.getObject().isPresent()) {
                stripeObject = dataObjectDeserializer.getObject().get();
            } else {
                // Deserialization failed, probably due to an API version mismatch
                log.error("DESERIALIZATION FAILED - EventID: {} - API Version: {} - SDK may be incompatible", webhookIdempotencyKey, event.getApiVersion());
                log.error("Raw event data: {}", event.getData().toJson());
                throw new RuntimeException("Cannot deserialize payment.created event - API version mismatch - EventID: " + webhookIdempotencyKey);
            }
            
            // Cast to Charge (payment.created returns Charge object)
            com.stripe.model.Charge charge = (com.stripe.model.Charge) stripeObject;
            
            String chargeId = charge.getId();
            String sourceTransferId = charge.getSourceTransfer();
            Long amountCents = charge.getAmount();
            

            String transactionId = null;
            if (sourceTransferId != null && !sourceTransferId.isEmpty()) {
                try {
                    Transfer transfer = Transfer.retrieve(sourceTransferId);
                    if (transfer.getMetadata() != null) {
                        transactionId = transfer.getMetadata().get("transaction_id");
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch transfer metadata - TransferID: {}", sourceTransferId, e);
                }
            }

            log.info("PAYMENT CREATED - User B RECEIVED FUNDS - AccountID: {} - ChargeID: {} - Amount: {} cents - SourceTransfer: {} - TransactionID: {} - EventID: {}", 
                    accountId, chargeId, amountCents, sourceTransferId, transactionId, webhookIdempotencyKey);

            if (transactionId != null && !transactionId.isEmpty()) {
                transactionService.handleStripeTransferCompleted(transactionId, sourceTransferId, webhookIdempotencyKey);
            } else {
                log.warn("Payment created without transaction_id in metadata - Cannot link to transaction. EventID: {}", webhookIdempotencyKey);
            }
            
        } catch (ClassCastException e) {
            log.error("Failed to cast event object to Charge - EventID: {} - Type: {}", event.getId(), event.getType(), e);
            throw new RuntimeException("Invalid event object type for payment.created", e);
        } catch (Exception e) {
            log.error("Failed to process payment.created event - EventID: {}", event.getId(), e);
            throw new RuntimeException("Failed to handle payment.created", e);
        }
    }

    /**
     * Simple helper to extract field from JSON string
     */
    private String extractJsonField(String json, String fieldName) {
        // Simple regex to extract field value: "fieldName":"value" or "fieldName":value
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"?([^,\"\\}]+)\"?";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * Handle transfer.reversed event - Transfer was reversed, need rollback
     */
    private void handleTransferReversed(Event event) {
        try {
            String webhookIdempotencyKey = event.getId();
            
            // Deserialize the nested object inside the event
            com.stripe.model.EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            com.stripe.model.StripeObject stripeObject = null;
            
            if (dataObjectDeserializer.getObject().isPresent()) {
                stripeObject = dataObjectDeserializer.getObject().get();
            } else {
                log.error("Failed to deserialize transfer.reversed event - EventID: {}", webhookIdempotencyKey);
                return;
            }
            
            Transfer transfer = (Transfer) stripeObject;
            String transferId = transfer.getId();

            log.warn("Transfer REVERSED - TransferID: {} - EventID: {}", transferId, webhookIdempotencyKey);

            transactionService.handleStripeTransferFailure(transferId, 
                    "transfer_reversed", "Transfer was reversed", webhookIdempotencyKey);
            
        } catch (Exception e) {
            log.error("Failed to process transfer.reversed event", e);
            throw new RuntimeException("Failed to handle transfer.reversed", e);
        }
    }

    /**
     * Handle transfer.failed event - Transfer failed, need rollback
     */
    private void handleTransferFailed(Event event) {
        try {
            String webhookIdempotencyKey = event.getId();
            
            // Deserialize the nested object inside the event
            com.stripe.model.EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            com.stripe.model.StripeObject stripeObject = null;
            
            if (dataObjectDeserializer.getObject().isPresent()) {
                stripeObject = dataObjectDeserializer.getObject().get();
            } else {
                log.error("Failed to deserialize transfer.failed event - EventID: {}", webhookIdempotencyKey);
                return;
            }
            
            Transfer transfer = (Transfer) stripeObject;
            String transferId = transfer.getId();

            log.warn("Transfer FAILED - TransferID: {} - EventID: {}", transferId, webhookIdempotencyKey);

            transactionService.handleStripeTransferFailure(transferId, 
                    "transfer_failed", "Transfer failed", webhookIdempotencyKey);
            
        } catch (Exception e) {
            log.error("Failed to process transfer.failed event", e);
            throw new RuntimeException("Failed to handle transfer.failed", e);
        }
    }

}

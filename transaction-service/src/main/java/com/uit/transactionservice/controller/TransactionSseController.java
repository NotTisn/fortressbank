package com.uit.transactionservice.controller;

import com.uit.transactionservice.service.TransactionSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE Controller for real-time transaction status updates.
 * 
 * Usage:
 * 1. Client calls POST /api/transactions/verify-otp successfully
 * 2. Client opens SSE connection: GET /api/transactions/sse/{transactionId}
 * 3. Client waits for "transaction-update" event
 * 4. When Stripe webhook arrives, server pushes update
 * 5. Client receives update and closes connection
 * 
 * Example JavaScript:
 * <pre>
 * const eventSource = new EventSource('/api/transactions/sse/' + transactionId);
 * 
 * eventSource.addEventListener('transaction-update', (event) => {
 *     const update = JSON.parse(event.data);
 *     if (update.status === 'COMPLETED') {
 *         showSuccess(update);
 *         eventSource.close();
 *     } else if (update.status === 'FAILED') {
 *         showError(update);
 *         eventSource.close();
 *     }
 * });
 * 
 * eventSource.addEventListener('connected', () => {
 *     console.log('SSE connected');
 * });
 * 
 * eventSource.onerror = () => {
 *     console.error('SSE error');
 *     // Fallback: poll API for status
 * };
 * </pre>
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionSseController {

    private final TransactionSseService sseService;

    /**
     * SSE endpoint for transaction status updates.
     * Connect immediately after OTP verification to receive real-time updates.
     * 
     * @param transactionId The transaction ID to subscribe to
     * @return SseEmitter for receiving events
     */
    @GetMapping(value = "/sse/{transactionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToTransaction(@PathVariable String transactionId) {
        log.info(" SSE subscription request for transaction: {}", transactionId);
        return sseService.subscribe(transactionId);
    }

    /**
     * Health check endpoint for SSE service
     * Returns number of active SSE connections
     */
    @GetMapping("/sse/health")
    public String sseHealth() {
        int activeConnections = sseService.getActiveConnectionCount();
        return String.format("{\"activeConnections\": %d}", activeConnections);
    }
}

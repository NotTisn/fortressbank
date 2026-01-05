package com.uit.transactionservice.repository;

import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, java.util.UUID> {

    Page<Transaction> findBySenderAccountId(String senderAccountId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    // New methods for Account Number based history
    Page<Transaction> findBySenderAccountNumber(String senderAccountNumber, Pageable pageable);

    Page<Transaction> findByReceiverAccountNumber(String receiverAccountNumber, Pageable pageable);

    Page<Transaction> findBySenderAccountNumberOrReceiverAccountNumber(String senderAccountNumber, String receiverAccountNumber, Pageable pageable);

    List<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT COALESCE(SUM(amount + fee_amount), 0) FROM transactions " +
           "WHERE sender_account_id = :accountId " +
           "AND DATE(created_at) = CURRENT_DATE " +
           "AND status IN ('COMPLETED', 'PROCESSING')",
           nativeQuery = true)
    BigDecimal calculateDailyUsed(@Param("accountId") String accountId);

    @Query(value = "SELECT COALESCE(SUM(amount + fee_amount), 0) FROM transactions " +
           "WHERE sender_account_id = :accountId " +
           "AND EXTRACT(YEAR FROM created_at) = EXTRACT(YEAR FROM CURRENT_DATE) " +
           "AND EXTRACT(MONTH FROM created_at) = EXTRACT(MONTH FROM CURRENT_DATE) " +
           "AND status IN ('COMPLETED', 'PROCESSING')",
           nativeQuery = true)
    BigDecimal calculateMonthlyUsed(@Param("accountId") String accountId);

    /**
     * Find transaction by Stripe transfer ID
     */
    java.util.Optional<Transaction> findByStripeTransferId(String stripeTransferId);

    boolean existsByExternalTransactionId(String externalTransactionId);

    /**
     * Find transactions stuck in EXTERNAL_INITIATED status for webhook timeout detection
     * Used by StripeWebhookTimeoutJob to poll Stripe API
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.currentStep = :step AND t.createdAt < :createdBefore")
    List<Transaction> findByStatusAndCurrentStepAndCreatedAtBefore(
            @Param("status") TransactionStatus status,
            @Param("step") com.uit.transactionservice.entity.SagaStep step,
            @Param("createdBefore") LocalDateTime createdBefore
    );
}

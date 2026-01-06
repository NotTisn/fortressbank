package com.uit.transactionservice.repository;

import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
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

    // ========== Dashboard Statistics Queries ==========

    /**
     * Count total transactions in date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.createdAt BETWEEN :startDate AND :endDate")
    Long countTransactionsByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count transactions by status in date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.status = :status " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Long countTransactionsByStatusAndDateRange(
            @Param("status") TransactionStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count transactions by type in date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.transactionType = :type " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Long countTransactionsByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate total volume (sum of amounts) for successful transactions
     */
    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE status IN ('SUCCESS', 'COMPLETED') " +
           "AND created_at BETWEEN :startDate AND :endDate",
           nativeQuery = true)
    BigDecimal calculateTotalVolumeByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate total fees collected
     */
    @Query(value = "SELECT COALESCE(SUM(fee_amount), 0) FROM transactions " +
           "WHERE status IN ('SUCCESS', 'COMPLETED') " +
           "AND created_at BETWEEN :startDate AND :endDate",
           nativeQuery = true)
    BigDecimal calculateTotalFeesByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate average transaction amount
     */
    @Query(value = "SELECT COALESCE(AVG(amount), 0) FROM transactions " +
           "WHERE status IN ('SUCCESS', 'COMPLETED') " +
           "AND created_at BETWEEN :startDate AND :endDate",
           nativeQuery = true)
    BigDecimal calculateAverageAmountByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}

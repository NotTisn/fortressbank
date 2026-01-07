package com.uit.transactionservice.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatisticsResponse {

    // Time period info
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String periodLabel;

    // Transaction metrics
    private Long totalTransactions;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Long pendingTransactions;

    // Transaction volume (monetary)
    private BigDecimal totalVolume;
    private BigDecimal totalFees;
    private BigDecimal averageTransactionAmount;

    // Transaction breakdown by type
    private Long internalTransfers;
    private Long externalTransfers;
    private Long deposits;
    private Long withdrawals;
    private Long billPayments;

    // Account statistics
    private AccountStatisticsDto accountStats;
}

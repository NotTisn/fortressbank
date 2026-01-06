package com.uit.transactionservice.service;

import com.uit.transactionservice.client.AccountServiceClient;
import com.uit.transactionservice.dto.request.DashboardStatisticsRequest;
import com.uit.transactionservice.dto.response.AccountStatisticsDto;
import com.uit.transactionservice.dto.response.DashboardStatisticsResponse;
import com.uit.transactionservice.entity.TimePeriod;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    public DashboardStatisticsResponse getStatistics(DashboardStatisticsRequest request) {
        log.info("Fetching dashboard statistics for period: {}", request.getPeriod());

        // Step 1: Calculate date range based on period
        LocalDateTime startDate;
        LocalDateTime endDate;
        String periodLabel;

        if (request.getPeriod() == TimePeriod.CUSTOM) {
            startDate = request.getStartDate();
            endDate = request.getEndDate();
            periodLabel = formatCustomPeriod(startDate, endDate);
        } else {
            DateRange dateRange = calculateDateRange(request.getPeriod());
            startDate = dateRange.start;
            endDate = dateRange.end;
            periodLabel = getPeriodLabel(request.getPeriod());
        }

        log.debug("Date range: {} to {}", startDate, endDate);

        // Step 2: Fetch transaction metrics
        Long totalTransactions = transactionRepository.countTransactionsByDateRange(startDate, endDate);

        Long successfulTransactions = transactionRepository.countTransactionsByStatusAndDateRange(
            TransactionStatus.SUCCESS, startDate, endDate
        ) + transactionRepository.countTransactionsByStatusAndDateRange(
            TransactionStatus.COMPLETED, startDate, endDate
        );

        Long failedTransactions = transactionRepository.countTransactionsByStatusAndDateRange(
            TransactionStatus.FAILED, startDate, endDate
        );

        Long pendingTransactions = transactionRepository.countTransactionsByStatusAndDateRange(
            TransactionStatus.PENDING, startDate, endDate
        ) + transactionRepository.countTransactionsByStatusAndDateRange(
            TransactionStatus.PROCESSING, startDate, endDate
        );

        // Step 3: Calculate volumes
        BigDecimal totalVolume = transactionRepository.calculateTotalVolumeByDateRange(startDate, endDate);
        BigDecimal totalFees = transactionRepository.calculateTotalFeesByDateRange(startDate, endDate);
        BigDecimal avgAmount = transactionRepository.calculateAverageAmountByDateRange(startDate, endDate);

        // Round to 2 decimal places
        avgAmount = avgAmount.setScale(2, RoundingMode.HALF_UP);

        // Step 4: Transaction type breakdown
        Long internalTransfers = transactionRepository.countTransactionsByTypeAndDateRange(
            TransactionType.INTERNAL_TRANSFER, startDate, endDate
        );
        Long externalTransfers = transactionRepository.countTransactionsByTypeAndDateRange(
            TransactionType.EXTERNAL_TRANSFER, startDate, endDate
        );
        Long deposits = transactionRepository.countTransactionsByTypeAndDateRange(
            TransactionType.DEPOSIT, startDate, endDate
        );
        Long withdrawals = transactionRepository.countTransactionsByTypeAndDateRange(
            TransactionType.WITHDRAWAL, startDate, endDate
        );
        Long billPayments = transactionRepository.countTransactionsByTypeAndDateRange(
            TransactionType.BILL_PAYMENT, startDate, endDate
        );

        // Step 5: Fetch account statistics from account-service
        AccountStatisticsDto accountStats = accountServiceClient.getAccountStatistics();

        // Step 6: Build response
        DashboardStatisticsResponse response = DashboardStatisticsResponse.builder()
            .startDate(startDate)
            .endDate(endDate)
            .periodLabel(periodLabel)
            .totalTransactions(totalTransactions)
            .successfulTransactions(successfulTransactions)
            .failedTransactions(failedTransactions)
            .pendingTransactions(pendingTransactions)
            .totalVolume(totalVolume)
            .totalFees(totalFees)
            .averageTransactionAmount(avgAmount)
            .internalTransfers(internalTransfers)
            .externalTransfers(externalTransfers)
            .deposits(deposits)
            .withdrawals(withdrawals)
            .billPayments(billPayments)
            .accountStats(accountStats)
            .build();

        log.info("Dashboard statistics fetched - Total transactions: {}, Total volume: {}, Period: {}",
                totalTransactions, totalVolume, periodLabel);

        return response;
    }

    private DateRange calculateDateRange(TimePeriod period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        LocalDateTime end;

        switch (period) {
            case TODAY:
                start = now.toLocalDate().atStartOfDay();
                end = now.toLocalDate().atTime(LocalTime.MAX);
                break;

            case THIS_WEEK:
                start = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay();
                end = now.toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                    .atTime(LocalTime.MAX);
                break;

            case THIS_MONTH:
                start = now.toLocalDate()
                    .with(TemporalAdjusters.firstDayOfMonth())
                    .atStartOfDay();
                end = now.toLocalDate()
                    .with(TemporalAdjusters.lastDayOfMonth())
                    .atTime(LocalTime.MAX);
                break;

            case THIS_YEAR:
                start = now.toLocalDate()
                    .with(TemporalAdjusters.firstDayOfYear())
                    .atStartOfDay();
                end = now.toLocalDate()
                    .with(TemporalAdjusters.lastDayOfYear())
                    .atTime(LocalTime.MAX);
                break;

            default:
                throw new IllegalArgumentException("Invalid time period: " + period);
        }

        return new DateRange(start, end);
    }

    private String getPeriodLabel(TimePeriod period) {
        switch (period) {
            case TODAY: return "Today";
            case THIS_WEEK: return "This Week";
            case THIS_MONTH: return "This Month";
            case THIS_YEAR: return "This Year";
            default: return "Unknown Period";
        }
    }

    private String formatCustomPeriod(LocalDateTime start, LocalDateTime end) {
        return String.format("%s - %s",
            start.toLocalDate().toString(),
            end.toLocalDate().toString()
        );
    }

    // Inner class for date range
    private static class DateRange {
        final LocalDateTime start;
        final LocalDateTime end;

        DateRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}

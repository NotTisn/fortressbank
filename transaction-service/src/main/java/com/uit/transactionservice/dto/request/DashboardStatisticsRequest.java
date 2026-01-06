package com.uit.transactionservice.dto.request;

import com.uit.transactionservice.entity.TimePeriod;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatisticsRequest {

    private TimePeriod period;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @AssertTrue(message = "When period is CUSTOM, both startDate and endDate are required and startDate must be before endDate")
    public boolean isValidCustomRange() {
        if (period == TimePeriod.CUSTOM) {
            return startDate != null && endDate != null && !startDate.isAfter(endDate);
        }
        return true;
    }
}

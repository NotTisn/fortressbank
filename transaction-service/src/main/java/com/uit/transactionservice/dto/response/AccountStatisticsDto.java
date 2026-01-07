package com.uit.transactionservice.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStatisticsDto {
    private Long totalAccounts;
    private Long activeAccounts;
    private Long lockedAccounts;
    private Long closedAccounts;
}

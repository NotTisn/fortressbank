package com.uit.accountservice.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStatisticsResponse {
    private Long totalAccounts;
    private Long activeAccounts;
    private Long lockedAccounts;
    private Long closedAccounts;
}

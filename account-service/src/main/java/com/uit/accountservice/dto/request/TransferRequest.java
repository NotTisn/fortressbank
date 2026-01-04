package com.uit.accountservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String senderAccountId;
    private String receiverAccountId;
    private BigDecimal amount;
}
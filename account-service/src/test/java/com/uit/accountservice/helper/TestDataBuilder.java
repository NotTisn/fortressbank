package com.uit.accountservice.helper;

import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Test data builder for creating test accounts
 */
public class TestDataBuilder {

    private static final Random random = new Random();

    /**
     * Create a default test account
     */
    public static Account createDefaultAccount() {
        return Account.builder()
                .accountNumber(generateAccountNumber())
                .userId("test-user-" + random.nextInt(1000))
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin-" + random.nextInt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create an account with specific balance
     */
    public static Account createAccountWithBalance(BigDecimal balance) {
        return Account.builder()
                .accountNumber(generateAccountNumber())
                .userId("test-user-" + random.nextInt(1000))
                .balance(balance)
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin-" + random.nextInt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create an account for specific user
     */
    public static Account createAccountForUser(String userId) {
        return Account.builder()
                .accountNumber(generateAccountNumber())
                .userId(userId)
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin-" + random.nextInt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create an account with specific status
     */
    public static Account createAccountWithStatus(AccountStatus status) {
        return Account.builder()
                .accountNumber(generateAccountNumber())
                .userId("test-user-" + random.nextInt(1000))
                .balance(BigDecimal.valueOf(1000.00))
                .status(status)
                .pinHash("hashed-pin-" + random.nextInt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a fully customized account
     */
    public static Account createAccount(String userId, String accountNumber, 
                                       BigDecimal balance, AccountStatus status) {
        return Account.builder()
                .accountNumber(accountNumber)
                .userId(userId)
                .balance(balance)
                .status(status)
                .pinHash("hashed-pin-" + random.nextInt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Generate random account number
     */
    private static String generateAccountNumber() {
        return String.format("%010d", random.nextInt(1000000000));
    }
}

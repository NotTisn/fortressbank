package com.uit.accountservice.repository;

import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    List<Account> findByUserId(@Param("userId") String userId);

    /**
     * Find account with pessimistic write lock to prevent concurrent modifications.
     * This ensures only one transaction can modify the account at a time.
     * Prevents race conditions when multiple transfers affect the same account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdWithLock(@Param("accountId") String accountId);

    /**
     * Find multiple accounts with pessimistic write lock.
     * Used for atomic internal transfers to lock both sender and receiver.
     * Locks are acquired in deterministic order (by accountId) to prevent deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId IN :accountIds ORDER BY a.accountId")
    List<Account> findByIdInWithLock(@Param("accountIds") List<String> accountIds);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByUserId(String userId);

    /**
     * Count accounts by status for dashboard statistics
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.status = :status")
    Long countByStatus(@Param("status") AccountStatus status);
}

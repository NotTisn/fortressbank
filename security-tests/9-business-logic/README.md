# Business Logic Attack Tests

This category tests for flaws in the application's business logic that can be exploited.

## Attack Vectors

### 1. Double-Spend Attack (`test-01-double-spend.ps1`)
Tests race condition exploitation for double-spending funds.

**What We Test:**
- Concurrent transfer requests to same account
- Balance verification after parallel execution
- Optimistic locking effectiveness
- Transaction isolation levels

**Attack Technique:**
```
Thread 1: Read balance ($1000) → Initiate transfer ($900)
Thread 2: Read balance ($1000) → Initiate transfer ($900)  [Concurrent]
Result: Both succeed → $1800 transferred from $1000 balance
```

**OWASP Coverage:** A04:2021 - Insecure Design

### 2. State Machine Abuse (`test-02-state-machine-abuse.ps1`)
Tests if transaction state transitions can be manipulated.

**What We Test:**
- OTP verification on non-existent transactions
- IDOR on other users' transactions
- Skip OTP verification step
- Cancel then complete race condition
- Replay completed transaction IDs
- Amount modification after initiation

**OWASP Coverage:** A04:2021 - Insecure Design

### 3. Fee Manipulation (`test-03-fee-manipulation.ps1`)
Tests fee calculation vulnerabilities.

**What We Test:**
- Negative fee injection (earn money on transfer)
- Zero fee bypass
- Precision/rounding exploits (0.001 → 0)
- Integer overflow attacks
- Currency mismatch exploitation

**OWASP Coverage:** A03:2021 - Injection

## Running Tests

```powershell
# Run all business logic tests
.\run-business-tests.ps1

# Run individual test
.\test-01-double-spend.ps1
```

## Expected Results

Secure system should:
- ✅ Use optimistic locking with version checking
- ✅ Enforce strict state machine transitions
- ✅ Validate all financial calculations server-side
- ✅ Use SERIALIZABLE isolation for financial transactions

## Mitigations Required

### Double-Spend Prevention
1. **Optimistic Locking:** `@Version` annotation on Account entity
2. **Pessimistic Locking:** `SELECT FOR UPDATE` for critical operations
3. **Transaction Isolation:** SERIALIZABLE level for transfers
4. **Idempotency Keys:** Prevent duplicate request processing

### State Machine Protection
1. **Explicit State Checks:** Verify current state before transition
2. **Owner Verification:** Transaction belongs to authenticated user
3. **Immutable History:** Never modify completed transactions
4. **Audit Trail:** Log all state transitions

### Fee Security
1. **Server-Side Calculation:** Never trust client fee values
2. **BigDecimal Math:** Avoid floating-point for money
3. **Range Validation:** Fees must be positive, within limits
4. **Overflow Protection:** Use checked arithmetic operations

# Timing Attack Tests

This category tests for information leakage through timing side channels.

## Attack Vectors

### 1. Account Enumeration (`test-01-account-enumeration.ps1`)
Tests if valid accounts can be discovered through response time differences.

**What We Test:**
- Login response times for valid vs invalid usernames
- Statistical analysis of timing patterns
- Constant-time comparison verification

**Attack Technique:**
```
Valid user:   DB lookup → password hash → compare → reject
Invalid user: DB lookup → not found → reject (faster)
```

The time difference can reveal which usernames exist.

**OWASP Coverage:** A01:2021 - Broken Access Control

### 2. Password Reset Enumeration (`test-02-password-reset-enum.ps1`)
Tests if password reset reveals account existence.

**What We Test:**
- Response message comparison (different messages = enumeration)
- HTTP status code differences
- Response timing analysis

**OWASP Coverage:** A01:2021 - Broken Access Control

## Running Tests

```powershell
# Run all timing tests
.\run-timing-tests.ps1

# Run individual test
.\test-01-account-enumeration.ps1
```

## Expected Results

Secure system characteristics:
- ✅ Response times are statistically similar (±10ms variance)
- ✅ Same error message for valid and invalid users
- ✅ Same HTTP status code regardless of account existence

## Mitigations Required

1. **Constant-Time Comparison:** Use `MessageDigest.isEqual()` or similar
2. **Generic Error Messages:** "Invalid username or password" - never reveal which
3. **Fixed Response Delay:** Add artificial delay to normalize response times
4. **Rate Limiting:** Prevent statistical analysis via request limits
5. **CAPTCHA:** Require CAPTCHA after N failed attempts

## Technical Notes

Timing attacks require multiple samples for statistical significance:
- Minimum 20-50 requests per test case
- Calculate mean, standard deviation, t-test
- Variance > 50ms is concerning
- Variance < 10ms indicates constant-time implementation

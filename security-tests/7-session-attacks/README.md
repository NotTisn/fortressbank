# Session Attack Tests

This category tests session management security vulnerabilities in FortressBank.

## Attack Vectors

### 1. Concurrent Session Abuse (`test-01-concurrent-sessions.ps1`)
Tests if the system properly handles multiple simultaneous sessions for the same user.

**What We Test:**
- Maximum session limits
- Old token validity after new login
- Geographic velocity detection (impossible travel)
- Device fingerprint consistency

**OWASP Coverage:** A07:2021 - Identification and Authentication Failures

### 2. Token Theft Simulation (`test-02-token-theft.ps1`)
Simulates scenarios where an attacker has obtained a user's valid JWT token.

**What We Test:**
- Account enumeration via stolen token
- Transaction history exfiltration
- PII extraction capabilities
- Unauthorized transaction attempts
- Device registration persistence

**OWASP Coverage:** A01:2021 - Broken Access Control

### 3. Refresh Token Abuse (`test-03-refresh-token-abuse.ps1`)
Tests refresh token security mechanisms.

**What We Test:**
- Token reuse after rotation (one-time use enforcement)
- IP binding requirements
- Token validity after logout
- Brute force protection

**OWASP Coverage:** A07:2021 - Identification and Authentication Failures

## Running Tests

```powershell
# Run all session tests
.\run-session-tests.ps1

# Run individual test
.\test-01-concurrent-sessions.ps1
```

## Expected Results

All tests should FAIL (from attacker's perspective) if the system is secure:
- ✅ PASS = Attack blocked = System secure
- ❌ FAIL = Attack succeeded = Vulnerability found

## Mitigations Required

1. **Session Limits:** Enforce max 3-5 concurrent sessions per user
2. **Token Binding:** Bind tokens to device fingerprint and IP range
3. **Refresh Token Rotation:** Single-use refresh tokens with revocation on reuse
4. **Logout Invalidation:** Immediately revoke all tokens on logout
5. **Geo-Velocity:** Flag logins from impossible geographic distances

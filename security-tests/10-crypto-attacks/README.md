# Cryptographic Attack Tests

This category tests cryptographic security vulnerabilities in FortressBank's Smart OTP and authentication systems.

## Attack Vectors

### 1. Weak Key Detection (`test-01-weak-keys.ps1`)
Tests if the system accepts weak or insecure cryptographic keys.

**What We Test:**
- Empty or minimal length keys
- All-zeros / all-ones patterns
- Short RSA keys (512-bit)
- Known test keys from documentation
- Malformed PEM structures
- Private key instead of public
- Key reuse detection

**OWASP Coverage:** A02:2021 - Cryptographic Failures

### 2. Algorithm Confusion (`test-02-algorithm-confusion.ps1`)
Tests for algorithm substitution and downgrade attacks.

**What We Test:**
- 'none' algorithm acceptance (CVE-style attack)
- RS256 → HS256 downgrade (using public key as HMAC secret)
- Invalid elliptic curve point attacks
- Timing leakage in signature verification
- Key type confusion (DSA, certificate vs raw key)

**Attack Technique (HS256 Downgrade):**
```
1. Obtain server's public RSA key
2. Create JWT with alg: HS256
3. Sign with public key as HMAC secret
4. If server uses public key for verification → Attack succeeds!
```

**OWASP Coverage:** A02:2021 - Cryptographic Failures

### 3. Entropy Analysis (`test-03-entropy-analysis.ps1`)
Tests randomness quality of generated tokens and challenges.

**What We Test:**
- Sequential pattern detection in IDs
- Timestamp-based predictability
- Collision detection
- Character distribution uniformity
- OTP brute-force feasibility
- JWT JTI (token ID) entropy

**OWASP Coverage:** A02:2021 - Cryptographic Failures

## Running Tests

```powershell
# Run all crypto tests
.\run-crypto-tests.ps1

# Run individual test
.\test-01-weak-keys.ps1
```

## Expected Results

Secure system should:
- ✅ Reject keys below RSA-2048 or EC P-256
- ✅ Explicitly allowlist algorithms (RS256 only)
- ✅ Validate key type matches expected algorithm
- ✅ Use UUID v4 (128-bit entropy) for challenge IDs
- ✅ Implement constant-time signature comparison

## Mitigations Required

### Key Security
```java
// Minimum key size validation
if (rsaKey.getModulus().bitLength() < 2048) {
    throw new WeakKeyException("RSA key must be at least 2048 bits");
}
```

### Algorithm Allowlist
```java
// Explicit algorithm allowlist
private static final Set<String> ALLOWED_ALGORITHMS = Set.of("RS256", "RS384", "RS512");

if (!ALLOWED_ALGORITHMS.contains(jwt.getAlgorithm())) {
    throw new InvalidAlgorithmException("Algorithm not allowed: " + jwt.getAlgorithm());
}
```

### Secure Randomness
```java
// Use SecureRandom for all security-sensitive values
SecureRandom random = new SecureRandom();
byte[] challengeBytes = new byte[32]; // 256 bits
random.nextBytes(challengeBytes);
String challengeId = Base64.getUrlEncoder().encodeToString(challengeBytes);
```

## Reference Standards

- **NIST SP 800-90A/B:** Random Number Generation
- **NIST SP 800-57:** Key Management Recommendations
- **RFC 7518:** JSON Web Algorithms (JWA)
- **CVE-2015-9235:** JWT 'none' algorithm vulnerability

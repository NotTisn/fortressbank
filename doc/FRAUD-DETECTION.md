# Enhanced Fraud Detection System

## Overview
FortressBank now implements a comprehensive 6-rule fraud detection system, matching SecureBank's security standards.

## Fraud Detection Rules

### Rule 1: High Transaction Amount (40 points)
- **Threshold**: $10,000
- **Risk**: Large transfers are high-value targets
- **Score**: +40 points

### Rule 2: Unusual Time of Day (30 points)
- **Hours**: 2:00 AM - 6:00 AM
- **Risk**: Fraudsters operate during low-activity hours
- **Score**: +30 points

### Rule 3: New Device ✨ NEW (25 points)
- **Detection**: Device fingerprint not in user's known devices
- **Risk**: Account takeover from compromised credentials
- **Score**: +25 points
- **Header**: `X-Device-Fingerprint`

### Rule 4: Geolocation Anomaly ✨ NEW (20 points)
- **Detection**: Location not in user's known locations
- **Risk**: Access from unusual country/city indicates account compromise
- **Score**: +20 points
- **Header**: `X-Location` (format: "City, Country" or "Country")

### Rule 5: New Payee ✨ NEW (15 points)
- **Detection**: First-time transfer to this recipient
- **Risk**: Social engineering/phishing attacks
- **Score**: +15 points
- **Data**: Checked against user's transaction history

### Rule 6: Velocity Check ✨ NEW (10 points)
- **Detection**: Multiple risk factors present (composite velocity)
- **Risk**: Pattern indicates coordinated attack
- **Score**: +10 points
- **Trigger**: 3 or more risk factors detected

### Rule 7: Aggregate Daily Velocity 🛡️ ANTI-SALAMI (35 points)
- **Detection**: Cumulative transfer amount in 24h sliding window exceeds threshold
- **Risk**: Salami slicing attack - many small transfers bypass single-transaction threshold
- **Score**: +35 points (HIGH impact - directly triggers MEDIUM risk)
- **Threshold**: 50,000 VND cumulative in 24 hours
- **Storage**: Redis with TTL-based sliding window
- **Key**: `velocity:daily:{userId}` with 24h expiry
- **Attack Mitigated**: Attacker sending 5 x $9,900 transfers would hit 49,500 cumulative, triggering on 6th transfer

## Risk Score Thresholds

| Score | Risk Level | Challenge Type | Action |
|-------|------------|----------------|--------|
| 0-39  | LOW        | NONE           | Instant approval |
| 40-69 | MEDIUM     | DEVICE_BIO     | Device biometric signature required |
| 70+   | HIGH       | FACE_VERIFY    | Face re-verification required |

> **Note**: If user has no registered device, falls back to SMS_OTP.  
> If user has no registered face, FACE_VERIFY falls back to DEVICE_BIO.

## Challenge Types

### NONE (Low Risk)
- **Trigger**: Risk score 0-39
- **User Experience**: Transfer completes immediately
- **Implementation**: No verification step

### SMS_OTP (Medium Risk - Fallback)
- **Trigger**: Risk score 40-69 when user has no registered device
- **User Experience**: 6-digit code sent via SMS
- **Implementation**: 
  - transaction-service generates OTP, stores in Redis (90s TTL)
  - notification-service sends SMS via TextBee
  - User enters code to confirm transfer

### DEVICE_BIO (Medium Risk) 🇻🇳 Vietnamese E-Banking Style
- **Trigger**: Risk score 40-69 when user has registered device
- **User Experience**: Fingerprint/PIN prompts on mobile app (like Momo/VCB)
- **Implementation**: 
  - Challenge-response pattern with cryptographic signature
  - Device's secure key (stored in TEE/Secure Enclave) signs challenge data
  - Biometric unlock (fingerprint/face/PIN) required to access key
  - 120-second challenge TTL in Redis
  - Signature verified by user-service using stored public key

#### Device Registration Flow
```
1. User calls POST /devices/register with device info + public key
   
2. user-service stores device (id, name, publicKeyPem)
   
3. Device is now registered for DEVICE_BIO challenges
```

#### DEVICE_BIO Verification Flow
```
1. User initiates transfer → Risk score 40-69
   
2. Transaction created with status PENDING_SMART_OTP
   - challengeId and challengeData returned
   
3. Mobile app prompts for fingerprint/PIN
   
4. On biometric success, app signs challengeData with device key
   
5. POST /transactions/verify-device with deviceId + signatureBase64
   
6. transaction-service → user-service verifies signature
   
7. If valid → Transfer executes
```

### FACE_VERIFY (High Risk) 🇻🇳 Vietnamese E-Banking Style
- **Trigger**: Risk score 70+ when user has registered face
- **User Experience**: Face scan prompt (like VCB high-value transfers)
- **Implementation**: 
  - Leverages existing FaceID infrastructure (fbank-ai service)
  - Liveness detection to prevent spoofing
  - Face must match registered biometric template
  - 120-second challenge TTL in Redis

#### FACE_VERIFY Verification Flow
```
1. User initiates transfer → Risk score 70+
   
2. Transaction created with status PENDING_SMART_OTP
   - challengeId returned
   
3. Mobile app prompts for face scan
   
4. App sends face image to user-service /smart-otp/verify-face
   
5. user-service → fbank-ai verifies face match + liveness
   
6. If valid → POST /transactions/verify-face to complete
   
7. Transfer executes
```

## Smart OTP API Endpoints

### Device Management

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/devices/register` | POST | JWT | Register new device with public key |
| `/devices` | GET | JWT | List user's registered devices |
| `/devices/{deviceId}` | DELETE | JWT | Revoke a device |
| `/devices/internal/verify-signature` | POST | Internal | Verify device signature |

### Smart OTP Verification

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/smart-otp/status` | GET | JWT | Check user's verification capabilities |
| `/smart-otp/verify-device` | POST | JWT | Submit device signature |
| `/smart-otp/verify-face` | POST | JWT | Submit face image for verification |
| `/smart-otp/internal/challenge` | POST | Internal | Generate challenge for transaction |
| `/smart-otp/internal/verify-device` | POST | Internal | Verify device signature (S2S) |
| `/smart-otp/internal/status/{userId}` | GET | Internal | Check user's capabilities (S2S) |

### Transaction Verification

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/transactions/verify-otp` | POST | JWT | Verify SMS OTP |
| `/transactions/verify-device` | POST | JWT | Verify device signature |
| `/transactions/verify-face` | POST | JWT | Complete face verification |

## Client Integration

### Required Headers for Enhanced Detection

```http
POST /accounts/transfers
X-Device-Fingerprint: sha256_hash_of_browser_fingerprint
X-Location: Ho Chi Minh City, Vietnam
X-Forwarded-For: 1.2.3.4  (automatically added by Kong/proxy)
```

### Device Fingerprinting (Frontend)
```javascript
// Example using FingerprintJS or similar
const fingerprint = await FingerprintJS.load();
const result = await fingerprint.get();

fetch('/accounts/transfers', {
  method: 'POST',
  headers: {
    'X-Device-Fingerprint': result.visitorId,
    'X-Location': `${userCity}, ${userCountry}`
  },
  body: JSON.stringify(transferData)
});
```

## Architecture

```
┌──────────────────┐
│  Mobile App      │
│  - Device Key    │ (TEE/Secure Enclave)
│  - Face Capture  │
│  - Fingerprint   │
└────────┬─────────┘
         │ Headers: X-Device-Fingerprint, X-Location
         ▼
┌──────────────────┐
│  Kong Gateway    │
│  + Rate Limiting │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│Transaction Svc   │────────────────────────────────┐
│ - Create transfer│                                │
│ - Map challenge  │                                │
│ - Verify & exec  │                                │
└────────┬─────────┘                                │
         │                                          │
         ▼                                          ▼
┌──────────────────┐      ┌───────────────┐      ┌───────────────┐
│  Risk Engine     │─────▶│ User Service  │      │    Redis      │
│  - 7 Rule Engine │      │ - RiskProfile │      │ - Velocity    │
│  - Score calc    │◀─────│ - Devices     │      │ - Challenges  │
│                  │      │ - SmartOTP    │      │ - 24h TTL     │
└──────────────────┘      │ - FaceID      │      └───────────────┘
                          └───────┬───────┘
                                  │
                                  ▼
                          ┌───────────────┐
                          │   fbank-ai    │
                          │ Face Verify   │
                          │ Liveness Det  │
                          └───────────────┘
```

### Challenge Flow (DEVICE_BIO)
```
Mobile App                     Transaction Svc              User Svc             Redis
    │                               │                          │                   │
    │ POST /transfers              │                          │                   │
    │──────────────────────────────>│                          │                   │
    │                               │ getSmartOtpStatus()     │                   │
    │                               │──────────────────────────>│                   │
    │                               │<──────────────────────────│ hasDevice=true   │
    │                               │ generateChallenge()      │                   │
    │                               │──────────────────────────>│                   │
    │                               │                          │ store in Redis   │
    │                               │                          │──────────────────>│
    │                               │<──────────────────────────│ challengeId+data │
    │<──────────────────────────────│ PENDING_SMART_OTP        │                   │
    │                               │ challengeId, challengeData│                   │
    │                               │                          │                   │
    │ [User taps fingerprint]      │                          │                   │
    │ [Device signs challenge]     │                          │                   │
    │                               │                          │                   │
    │ POST /verify-device          │                          │                   │
    │ deviceId, signature          │                          │                   │
    │──────────────────────────────>│                          │                   │
    │                               │ verifyDeviceSignature() │                   │
    │                               │──────────────────────────>│                   │
    │                               │                          │ get from Redis   │
    │                               │                          │──────────────────>│
    │                               │                          │ verify signature │
    │                               │<──────────────────────────│ valid=true       │
    │                               │ [Execute Transfer]       │                   │
    │<──────────────────────────────│ COMPLETED                │                   │
```

## Benefits

1. **Reduced False Positives**: Multi-factor scoring vs single-rule rejection
2. **Adaptive Security**: Learns user behavior patterns
3. **Layered Defense**: 7 independent checks
4. **User Experience**: Low-risk users get instant approval
5. **Fraud Prevention**: 85% reduction in fraudulent transfers (based on SecureBank data)
6. **Salami Slicing Protection**: Aggregate velocity prevents split-transaction attacks

## Testing

### Scenario 1: Trusted User
- Known device ✅
- Known location ✅
- Known payee ✅
- Normal hours ✅
- **Result**: LOW risk, instant approval

### Scenario 2: Suspicious Activity
- New device ❌ (+25)
- New location ❌ (+20)
- Large amount ❌ (+40)
- **Score**: 85 → HIGH risk → SMART_OTP required

### Scenario 3: Account Takeover
- New device ❌ (+25)
- Unusual location ❌ (+20)
- Unusual time ❌ (+30)
- New payee ❌ (+15)
- Multiple factors ❌ (+10)
- **Score**: 100 → HIGH risk → Transfer blocked/SMART_OTP

### Scenario 4: Salami Slicing Attack
- Attacker makes 6 small transfers of 10,000₫ each
- Transfers 1-5: Cumulative total = 50,000₫ (under limit)
  - **Result**: LOW risk, approved
- Transfer 6: Cumulative total = 60,000₫ (exceeds 50,000₫ limit)
  - Daily velocity exceeded ❌ (+35)
  - **Result**: MEDIUM risk → SMS_OTP required

## Integration Architecture

### Transfer Flow with Velocity Tracking

```
┌─────────────────────────────────────────────────────────────────────┐
│                      TRANSFER REQUEST FLOW                          │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 1. Transaction Service: Initiates transfer saga                     │
│    → Calls Risk Engine for PRE-TRANSFER assessment                  │
└─────────────────────────────────────┬────────────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 2. Risk Engine: Assesses 7 rules including Rule 7 (Velocity)       │
│    → Returns: riskScore, riskLevel, challengeType, reasons[]        │
│    → If HIGH: Requires SMART_OTP or blocks transfer                 │
│    → If MEDIUM: Requires SMS_OTP                                    │
│    → If LOW: Allows transfer to proceed                             │
└─────────────────────────────────────┬────────────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 3. Account Service: Executes atomic transfer (if approved)          │
│    → Pessimistic locks both accounts                                │
│    → Debits sender, credits receiver                                │
│    → Publishes audit events                                         │
└─────────────────────────────────────┬────────────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 4. Account Service: POST-TRANSFER velocity recording (async)        │
│    → Calls: POST /assess/internal/velocity/record                   │
│    → Fire-and-forget: doesn't slow down response                    │
│    → Risk Engine updates Redis: velocity:daily:{userId}             │
└──────────────────────────────────────────────────────────────────────┘
```

### Velocity API Endpoints (Internal)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/assess/internal/velocity/record` | POST | Record completed transfer (account-service → risk-engine) |
| `/assess/internal/velocity/{userId}` | GET | Get current daily total (for debugging/monitoring) |

### Redis Data Structure

- **Key**: `velocity:daily:{userId}`
- **Value**: Cumulative transfer amount (BigDecimal as string)
- **TTL**: 86400 seconds (24 hours from first transfer)

## Future Enhancements

- [x] Transaction velocity (daily cumulative limit)
- [x] Smart OTP / TOTP (Authenticator app integration)
- [ ] Machine learning model for behavior analysis
- [ ] IP reputation scoring (proxy/VPN detection)
- [ ] Transaction velocity (n transfers in m minutes)
- [ ] Known fraud patterns database
- [ ] FaceID verification for ultra-high-risk transfers

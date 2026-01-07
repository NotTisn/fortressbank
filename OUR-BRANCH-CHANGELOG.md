# FortressBank — Branch Audit & Changelog

> **Branch:** `security/penetration-tests`  
> **Author:** Phu Tho  
> **Period:** December 15, 2025 → January 5, 2026  
> **Total Commits:** 25 unique commits (not in main)  
> **Impact:** ~12,000+ lines added across 150+ files  

---

## 🎯 Executive Summary

This branch represents a complete **security hardening and developer experience overhaul** of FortressBank. The work spans four major initiatives:

| Initiative | Description | Business Value |
|------------|-------------|----------------|
| **Smart OTP System** | Vietnamese e-banking style 2FA with device binding and face verification | Bank-grade transaction security |
| **Security Testing Suite** | Automated penetration tests for OWASP Top 10 vulnerabilities | Compliance-ready security posture |
| **Developer Experience** | One-command local development with hybrid Docker mode | 10x faster onboarding, IDE debugging works |
| **Multi-Deployment Support** | Same codebase runs in Raw Maven, IDE, Hybrid, and Full Docker modes | No more "works on my machine" |

---

## 📊 Work Statistics

| Metric | Value |
|--------|-------|
| Commits | 25 |
| Files Changed | 150+ |
| Lines Added | ~12,000+ |
| New Services/Features | Smart OTP, Velocity Tracking, Device Management |
| New Test Suites | 6 security test categories, 13 unit tests |
| Documentation | 5 new docs, 3 major updates |

---

## 🔐 SECTION 1: SECURITY FEATURES

### 1.1 Smart OTP System (Vietnamese E-Banking Style)
**Commit:** `874345f` | **Date:** 2026-01-02 | **+3,228 lines**

Implemented complete device-bound and biometric verification system:

**New Endpoints:**
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/devices/register` | POST | Register device with public key |
| `/smart-otp/status` | GET | Check user 2FA capabilities |
| `/smart-otp/verify-device` | POST | Submit cryptographic signature |
| `/smart-otp/verify-face` | POST | Submit face image for verification |
| `/totp/enroll` | POST | Start TOTP enrollment |
| `/totp/confirm` | POST | Confirm TOTP with first code |
| `/totp/verify` | POST | Verify TOTP code |

**Risk-to-Challenge Mapping:**
| Risk Score | Level | Challenge Type |
|------------|-------|----------------|
| 0-39 | LOW | NONE |
| 40-69 | MEDIUM | DEVICE_BIO (or SMS_OTP fallback) |
| 70+ | HIGH | FACE_VERIFY |

**New Entities:**
- `OtpSecret` - TOTP secrets with recovery codes (user-service)
- `Device` - Registered devices with public keys (user-service)

**Database Migrations:**
- `V5__Add_TOTP_Secrets_Table.sql` (user-service)
- `V6__Add_Devices_Table.sql` (user-service)
- `V5__add_risk_fields.sql` (transaction-service)
- `V6__add_challenge_id.sql` (transaction-service)

---

### 1.2 Velocity Tracking (Anti-Salami Slicing)
**Commits:** `e0c46e8`, `088a6a4` | **Date:** 2026-01-02 | **+1,460 lines**

Prevents attackers from bypassing thresholds by splitting large transfers:

**The Attack:**
```
5 × 9,900 VND = 49,500 VND total
Each under 10,000 HIGH_AMOUNT_THRESHOLD
Without velocity: All 5 = LOW risk (no verification!)
```

**The Defense:**
- Redis-backed 24-hour sliding window per user
- Configurable daily limit (default: 50,000 VND)
- Transfer #6 triggers MEDIUM risk (+35 points)

**New Components:**
- `VelocityTrackingService` in risk-engine (Redis-backed)
- `Rule 7: Aggregate Daily Velocity Check` (+35 points if >50K daily)
- `/assess/internal/velocity/record` endpoint (internal)
- Async velocity recording in account-service (fire-and-forget)

**Unit Tests:** 13 new tests for `VelocityTrackingService`

---

### 1.3 JWT Security Hardening
**Commits:** `855679a`, `7c42351`, `36d5e1c` | **Date:** 2026-01-02/03

**Critical Fixes:**
1. **DELETE ParseUserInfoFilter** — Legacy garbage that bypassed Spring Security
2. **Split JWT Configuration** — Separate `jwk-set-uri` (fetch) from `expected-issuer` (validate)
3. **Remove test-user fallback** — `TransactionController` had a SECURITY HOLE!

**Before (Vulnerable):**
```java
// If no JWT, use test user (!!!)
String userId = authentication != null ? authentication.getName() : "test-user";
```

**After (Secure):**
```java
@AuthenticationPrincipal Jwt jwt  // Throws 401 if no JWT
String userId = jwt.getSubject();
```

**Controllers Fixed:**
- `AccountController` - Now uses `@AuthenticationPrincipal Jwt`
- `TransactionController` - Now uses `@AuthenticationPrincipal Jwt`

---

### 1.4 Input Validation Hardening
**Commit:** `2cba99c` | **Date:** 2026-01-02

Added `@Valid` and `@NotBlank` to all DTOs:
- `TotpController` - RecoveryCodeRequest, InternalTotpVerifyRequest
- `SmartOtpController` - DeviceVerifyRequest, InternalChallengeRequest
- `DeviceController` - DeviceVerifySignatureRequest

**OWASP Coverage:** A03:2021 (Injection)

---

### 1.5 Kong Gateway Security Routes
**Commits:** `4132f2e`, `8abe7ff` | **Date:** 2026-01-02/05

**New Routes Added:**
| Route | Service | Auth |
|-------|---------|------|
| `/devices` | devices-service | OIDC |
| `/smart-otp` | smart-otp-service | OIDC |
| `/totp` | totp-service | OIDC |

**Kong Hybrid Mode:**
- Created `kong.local.yml` using `host.docker.internal`
- Allows Kong (Docker) to reach services (localhost)
- Configurable via `${KONG_CONFIG:-kong.local.yml}`

---

## 🧪 SECTION 2: SECURITY TESTING SUITE

### 2.1 Smart OTP Security Tests
**Commit:** `4132f2e`, `8b02210` | **Location:** `security-tests/6-smart-otp/`

| Test | Attack Prevented |
|------|------------------|
| `test-01-challenge-replay.ps1` | Replay of used challenges |
| `test-02-device-ownership.ps1` | IDOR on device verification |
| `test-03-signature-forgery.ps1` | Invalid cryptographic signatures |
| `test-04-challenge-expiry.ps1` | Expired challenge abuse |
| `test-05-face-bypass.ps1` | Face verification bypass attempts |

### 2.2 Salami Slicing Test
**Commit:** `e0c46e8` | **File:** `test-salami-slicing.ps1`

Simulates 5+ small transfers to verify velocity tracking triggers MEDIUM risk.

### 2.3 Security Test Infrastructure
**Commit:** `e0c46e8` | **Files:**
- `setup-testuser.ps1` - Automated test user and token setup
- `run-security-tests.ps1` - Master test runner
- `.gitignore` - Exclude access tokens

---

## 🛠️ SECTION 3: DEVELOPER EXPERIENCE

### 3.1 One-Command Dev Mode
**Commit:** `439e3f7` | **Date:** 2026-01-02 | **+4,215 lines**

**The Problem:**
- 8 services to start manually
- Docker hostnames don't work locally
- No IDE debugging in Docker mode

**The Solution:**
```powershell
cd fortressbank_be/infrastructure
.\dev.bat              # Start all 8 services
.\dev.bat -status      # Check what's running
.\dev.bat -kill        # Stop everything
.\dev.bat -clean       # Clean after branch switch
.\dev.bat -infra       # Start Docker infra only
```

**Files Added:**
- `infrastructure/dev.bat` - Entry point
- `infrastructure/scripts/dev-mode.ps1` - Smart launcher (~710 lines)
- `infrastructure/compose-infra-only.yaml` - Docker dependencies
- `infrastructure/README.md` - Full documentation

**Spring Local Profiles:**
- Added `application-local.yml` to all 8 services
- Added `bootstrap-local.yml` to disable config-server lookup
- Added `*-local.yml` in config-server for centralized local config

### 3.2 Frontend Integration
**Commit:** `b1fc69f` | **Date:** 2026-01-05 | **+283 lines**

Extended dev.bat to support full-stack development:

| Flag | Action |
|------|--------|
| `-fe` | Start Expo dev server |
| `-feinstall` | Install npm dependencies |
| `-fekill` | Stop Node processes |
| `-full` | Start everything (infra + backend + frontend) |
| `-fullkill` | Stop all processes |

### 3.3 Branch Switching Workflow
**Commit:** `dcbbe84` | **Date:** 2026-01-03

**The Problem:**
Switching git branches leaves stale `.class` files that cause mysterious failures.

**The Solution:**
```powershell
.\dev.bat -kill        # Stop services
.\dev.bat -clean       # Clean all target/ folders
git checkout <branch>  # Switch branch
.\dev.bat              # Start fresh
```

---

## 🔧 SECTION 4: MULTI-DEPLOYMENT SUPPORT

### 4.1 Localhost Defaults with Env Overrides
**Commits:** `839d32e`, `4983a7b` | **Date:** 2026-01-03/04

**Pattern Applied to All Services:**
```yaml
# application.yml
eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
```

### 4.2 Deployment Scenarios
| Scenario | How It Works |
|----------|--------------|
| Raw Maven | Uses localhost defaults |
| IDE Run | Uses localhost defaults |
| dev.bat Hybrid | Sets `local` profile, infra in Docker |
| Full Docker | docker-compose.services.yml sets env vars |

### 4.3 Docker Compose Standardization
**Commit:** `3e79203` | **Date:** 2026-01-04

- All services read from team's `.env` file
- No more hardcoded `postgres/123456`
- Added `RABBITMQ_ERLANG_COOKIE` for Windows Docker Desktop fix

---

## 🧪 SECTION 5: CI/TEST FIXES

### 5.1 Test Isolation Fixes
**Commits:** `041d586`, `ee061b8`, `943ce30`, `02fa59f` | **Date:** 2026-01-02/03

**Problems Fixed:**
1. JwtConfig tries to connect to Keycloak in CI
2. Config-server lookup fails in CI
3. ParseUserInfoFilter blocks Spring Security Test jwt()

**Solutions:**
- `@ConditionalOnProperty(name = "jwt.enabled", matchIfMissing = true)`
- `bootstrap-test.yml` with `spring.cloud.config.enabled=false`
- Added `application-test.yml` with mock Stripe keys

---

## 📚 SECTION 6: DOCUMENTATION

### 6.1 New Documents
| File | Purpose |
|------|---------|
| `doc/API-TESTING-GUIDE.md` | Curl-based API testing without frontend |
| `infrastructure/README.md` | Dev mode tooling documentation |
| `security-tests/PROGRESS.md` | Security fix status tracking |

### 6.2 Major Updates
| File | Changes |
|------|---------|
| `doc/FRAUD-DETECTION.md` | +186 lines: Smart OTP, velocity tracking, Rule 7 |
| `.github/copilot-instructions.md` | Deployment scenarios, JWT gotchas, branch workflow |

---

## 📋 COMPLETE COMMIT LOG (Chronological)

| # | Hash | Date | Subject |
|---|------|------|---------|
| 1 | `637d635` | 2025-12-15 | fix: add missing /cards/internal and /beneficiaries/internal to permit list |
| 2 | `0001c6f` | 2026-01-01 | Merge branch 'main' into security/penetration-tests |
| 3 | `439e3f7` | 2026-01-02 | feat(infra): add one-command dev mode with Spring local profiles |
| 4 | `e0c46e8` | 2026-01-02 | feat(security): add velocity tracking to prevent salami slicing attacks |
| 5 | `088a6a4` | 2026-01-02 | feat(account-service): integrate velocity recording after transfers |
| 6 | `874345f` | 2026-01-02 | feat(smart-otp): Vietnamese e-banking style biometric verification |
| 7 | `11f6b73` | 2026-01-02 | chore: gitignore security test access tokens |
| 8 | `4132f2e` | 2026-01-02 | feat(security): add Smart OTP security tests and Kong routes |
| 9 | `2cba99c` | 2026-01-02 | security(validation): add @Valid and @NotBlank to DTOs |
| 10 | `8b02210` | 2026-01-02 | fix(security-tests): correct Smart OTP test logic for HTTP 200 responses |
| 11 | `36d5e1c` | 2026-01-02 | fix(kong): resolve OIDC token introspection failure |
| 12 | `7c42351` | 2026-01-02 | fix(security): add split JWT issuer configuration for multi-deployment |
| 13 | `943ce30` | 2026-01-02 | fix(tests): Fix CI test failures in account-service and transaction-service |
| 14 | `ee061b8` | 2026-01-03 | fix(tests): Add bootstrap-test.yml to disable config-server in CI |
| 15 | `041d586` | 2026-01-03 | fix(tests): Add @Profile('!test') to JwtConfig beans |
| 16 | `02fa59f` | 2026-01-03 | fix: backward-compatible JwtConfig using ConditionalOnProperty |
| 17 | `419d732` | 2026-01-03 | fix: restore ParseUserInfoFilter with improved compatibility |
| 18 | `dcbbe84` | 2026-01-03 | Add dev -clean command for branch switching workflow |
| 19 | `855679a` | 2026-01-03 | refactor: DELETE ParseUserInfoFilter, use proper JWT authentication |
| 20 | `839d32e` | 2026-01-03 | fix: use localhost defaults in application.yml for all deployment scenarios |
| 21 | `3e79203` | 2026-01-04 | fix: standardize docker-compose to use .env variables consistently |
| 22 | `b3c47c5` | 2026-01-04 | chore: update .gitignore to exclude all target/ folders and temp files |
| 23 | `4983a7b` | 2026-01-04 | chore: improve dev experience - localhost defaults and better error messages |
| 24 | `b1fc69f` | 2026-01-05 | feat(dev-tools): integrate frontend (Expo) into dev.bat workflow |
| 25 | `8abe7ff` | 2026-01-05 | feat(infra): add Kong hybrid mode config for local service testing |

---

## 🎯 INTEGRATION CHECKLIST

When merging to main, these are the KEY changes that must be preserved:

### Critical Security (DO NOT LOSE)
- [ ] `ParseUserInfoFilter` DELETED in account-service and transaction-service
- [ ] Controllers use `@AuthenticationPrincipal Jwt jwt`
- [ ] `TransactionController` throws 401 without JWT (no test-user fallback)
- [ ] `VelocityTrackingService` in risk-engine with Redis integration
- [ ] Smart OTP entities and migrations in user-service
- [ ] Kong routes for `/devices`, `/smart-otp`, `/totp`

### Developer Experience (HIGHLY RECOMMENDED)
- [ ] `infrastructure/dev.bat` and `scripts/dev-mode.ps1`
- [ ] `infrastructure/compose-infra-only.yaml`
- [ ] All `application-local.yml` and `bootstrap-local.yml` files
- [ ] `*-local.yml` configs in config-server

### Configuration Pattern (ESSENTIAL FOR TEAM)
- [ ] All `application.yml` files use `${ENV_VAR:localhost-default}` pattern
- [ ] `docker-compose.services.yml` sets Docker hostnames via env vars
- [ ] `JwtConfig.java` with split `jwk-set-uri` and `expected-issuer`

---

*Generated: January 5, 2026*  
*Classification: INTERNAL — Branch Audit Report*

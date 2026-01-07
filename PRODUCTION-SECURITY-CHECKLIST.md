# =============================================================================
# FORTRESSBANK - PRODUCTION DEPLOYMENT SECURITY CHECKLIST
# =============================================================================
#
# This document provides a comprehensive checklist for deploying FortressBank
# in a production environment. Each item must be verified before go-live.
#
# Version: 1.0
# Last Updated: 2024-12-13
# Classification: INTERNAL - SECURITY SENSITIVE
#
# =============================================================================

## TABLE OF CONTENTS

1. [Pre-Deployment Requirements](#1-pre-deployment-requirements)
2. [Infrastructure Security](#2-infrastructure-security)
3. [Identity & Access Management](#3-identity--access-management)
4. [Network Security](#4-network-security)
5. [Data Protection](#5-data-protection)
6. [Application Security](#6-application-security)
7. [Monitoring & Alerting](#7-monitoring--alerting)
8. [Incident Response](#8-incident-response)
9. [Compliance](#9-compliance)
10. [Deployment Commands](#10-deployment-commands)

---

## 1. PRE-DEPLOYMENT REQUIREMENTS

### 1.1 Secrets Generation

```bash
# Generate all production secrets
echo "DB_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)"
echo "KEYCLOAK_DB_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)"
echo "KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)"
echo "REDIS_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)"
echo "RABBITMQ_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)"
echo "SESSION_SECRET=$(openssl rand -base64 32)"
```

### 1.2 Checklist

- [ ] All secrets generated using cryptographically secure random generator
- [ ] Secrets stored in secrets manager (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault)
- [ ] `.env.prod` file created from template and NOT committed to git
- [ ] Verified `.gitignore` excludes all `.env*` files except `.env.example`
- [ ] TLS certificates obtained from trusted CA (not self-signed)
- [ ] Certificate chain complete and valid
- [ ] Domain DNS records configured
- [ ] Firewall rules reviewed and approved

---

## 2. INFRASTRUCTURE SECURITY

### 2.1 Container Security

- [ ] Using production Dockerfile (`Dockerfile.prod`)
- [ ] Containers run as non-root user (UID 1001)
- [ ] Read-only root filesystem where possible
- [ ] No privileged containers
- [ ] `no-new-privileges` security option enabled
- [ ] Resource limits configured (CPU, memory)
- [ ] Health checks defined for all services
- [ ] Base images from trusted registries only
- [ ] Images scanned for vulnerabilities (Trivy, Clair, etc.)

### 2.2 Docker/Kubernetes Security

- [ ] Docker daemon socket not exposed
- [ ] Docker content trust enabled (`export DOCKER_CONTENT_TRUST=1`)
- [ ] Network policies restrict inter-pod communication
- [ ] Pod security policies/standards enforced
- [ ] Secrets mounted as volumes, not environment variables (for K8s)

### 2.3 Host Security

- [ ] Host OS patched and hardened
- [ ] Unnecessary services disabled
- [ ] Host firewall configured
- [ ] SSH key-based authentication only
- [ ] SSH root login disabled
- [ ] Audit logging enabled (auditd)
- [ ] File integrity monitoring (AIDE, Tripwire)

---

## 3. IDENTITY & ACCESS MANAGEMENT

### 3.1 Keycloak Configuration

- [ ] **Brute force protection enabled** (`bruteForceProtected: true`)
- [ ] Account lockout after 5 failed attempts
- [ ] Password policy enforced (12+ chars, complexity, history)
- [ ] MFA/2FA enabled for admin accounts
- [ ] MFA/2FA available for all users
- [ ] Session timeouts configured (idle: 15 min, max: 8 hours)
- [ ] Admin console access restricted to internal network
- [ ] Event logging enabled for security events
- [ ] Admin events logging enabled
- [ ] Default admin password changed
- [ ] Kong client secret rotated (not using Keycloak default)

### 3.2 JWT Security

- [ ] JWT signature verification enabled (RS256)
- [ ] Issuer validation enabled
- [ ] Audience validation enabled
- [ ] Token expiration enforced (5 minutes max for access tokens)
- [ ] Refresh token rotation enabled
- [ ] Token revocation mechanism in place

### 3.3 Service Accounts

- [ ] Minimal privileges for all service accounts
- [ ] No shared credentials between services
- [ ] Service account credentials rotated regularly
- [ ] Audit trail for service account usage

---

## 4. NETWORK SECURITY

### 4.1 External Network

- [ ] Only port 443 (HTTPS) exposed to internet
- [ ] All HTTP traffic redirected to HTTPS
- [ ] TLS 1.2+ only (TLS 1.0/1.1 disabled)
- [ ] Strong cipher suites only
- [ ] HSTS header enabled (max-age: 1 year, includeSubDomains)
- [ ] CAA DNS records configured
- [ ] DDoS protection in place (Cloudflare, AWS Shield, etc.)
- [ ] WAF rules configured

### 4.2 Internal Network

- [ ] Database ports NOT exposed to host/internet
- [ ] Redis NOT exposed to host/internet
- [ ] RabbitMQ management UI NOT exposed
- [ ] Kong Admin API accessible only from localhost
- [ ] Internal services communicate over internal Docker network only
- [ ] Network segmentation between tiers (web, app, data)

### 4.3 API Gateway (Kong)

- [ ] Production Kong config (`kong.prod.yml`) in use
- [ ] Rate limiting enabled (Redis-backed for HA)
- [ ] Request size limits configured
- [ ] CORS properly configured (specific origins, not `*`)
- [ ] Security headers added (CSP, X-Frame-Options, etc.)
- [ ] IP restriction for admin routes
- [ ] Logging enabled for audit trail
- [ ] SSL verification enabled for upstream services

---

## 5. DATA PROTECTION

### 5.1 Database Security

- [ ] Strong passwords for all database users
- [ ] Separate database users per service (least privilege)
- [ ] Database connections use TLS
- [ ] Database audit logging enabled
- [ ] Automated backups configured
- [ ] Backup encryption enabled
- [ ] Backup restoration tested
- [ ] Point-in-time recovery configured

### 5.2 Encryption

- [ ] Data encrypted in transit (TLS everywhere)
- [ ] Sensitive data encrypted at rest
- [ ] Encryption keys properly managed
- [ ] Key rotation schedule defined

### 5.3 Data Handling

- [ ] PII access logged
- [ ] Data retention policies implemented
- [ ] Secure data deletion procedures
- [ ] No sensitive data in logs

---

## 6. APPLICATION SECURITY

### 6.1 Backend Services

- [ ] JWT signature verification enabled in all services
- [ ] RBAC properly implemented (`RoleCheckInterceptor` fixed)
- [ ] Input validation on all endpoints
- [ ] SQL injection protection (parameterized queries)
- [ ] XSS protection headers
- [ ] CSRF protection enabled
- [ ] Sensitive endpoints require authentication
- [ ] No debug endpoints in production

### 6.2 Dependencies

- [ ] All dependencies updated to latest secure versions
- [ ] Vulnerability scan passed (OWASP Dependency Check, Snyk)
- [ ] No known CVEs in production dependencies
- [ ] SBOM (Software Bill of Materials) generated

### 6.3 Code Security

- [ ] Static analysis passed (SonarQube, Checkmarx)
- [ ] No hardcoded secrets in code
- [ ] No sensitive information in error messages
- [ ] Exception handling doesn't leak stack traces

---

## 7. MONITORING & ALERTING

### 7.1 Logging

- [ ] Centralized logging configured (ELK, Splunk, etc.)
- [ ] Log retention policy defined (90+ days)
- [ ] Logs include correlation IDs
- [ ] Security events logged (auth failures, access denied)
- [ ] Financial transactions logged for audit
- [ ] Logs protected from tampering
- [ ] No sensitive data in logs (passwords, full card numbers)

### 7.2 Metrics

- [ ] Prometheus/metrics endpoints configured
- [ ] Dashboards created (Grafana)
- [ ] Key metrics monitored:
  - [ ] Request latency (P50, P95, P99)
  - [ ] Error rates
  - [ ] Authentication failures
  - [ ] Rate limit hits
  - [ ] Database connection pool usage
  - [ ] JVM memory and GC

### 7.3 Alerting

- [ ] Alert rules configured for:
  - [ ] Service health check failures
  - [ ] Error rate spikes
  - [ ] High latency
  - [ ] Authentication failure spikes
  - [ ] Rate limit exhaustion
  - [ ] Certificate expiration (30 days warning)
  - [ ] Disk space warnings
- [ ] Alert notification channels configured (PagerDuty, Slack, email)
- [ ] On-call rotation defined

---

## 8. INCIDENT RESPONSE

### 8.1 Preparation

- [ ] Incident response plan documented
- [ ] Runbooks created for common incidents
- [ ] Contact list maintained (security team, legal, PR)
- [ ] Communication templates prepared
- [ ] Forensic tools available

### 8.2 Detection

- [ ] Intrusion detection system (IDS) configured
- [ ] Anomaly detection for transaction patterns
- [ ] Failed login attempt monitoring
- [ ] Unusual API usage patterns monitored

### 8.3 Recovery

- [ ] Disaster recovery plan documented
- [ ] Recovery time objective (RTO) defined
- [ ] Recovery point objective (RPO) defined
- [ ] DR testing schedule established

---

## 9. COMPLIANCE

### 9.1 Financial Regulations

- [ ] PCI-DSS requirements addressed (if handling card data)
- [ ] Transaction logging for regulatory audit
- [ ] Data residency requirements met
- [ ] Anti-money laundering (AML) controls in place

### 9.2 Privacy

- [ ] GDPR requirements addressed (if applicable)
- [ ] Privacy policy updated
- [ ] Data processing agreements in place
- [ ] User consent mechanisms implemented

### 9.3 Documentation

- [ ] Security architecture documented
- [ ] Network diagrams up to date
- [ ] Data flow diagrams created
- [ ] Risk assessment completed
- [ ] Penetration test scheduled/completed

---

## 10. DEPLOYMENT COMMANDS

### 10.1 Build Production Images

```bash
# Build all services with production Dockerfile
docker build -f Dockerfile.prod --target account-service -t fortressbank/account-service:prod .
docker build -f Dockerfile.prod --target user-service -t fortressbank/user-service:prod .
docker build -f Dockerfile.prod --target transaction-service -t fortressbank/transaction-service:prod .
docker build -f Dockerfile.prod --target notification-service -t fortressbank/notification-service:prod .
docker build -f Dockerfile.prod --target discovery -t fortressbank/discovery:prod .
docker build -f Dockerfile.prod --target risk-engine -t fortressbank/risk-engine:prod .
```

### 10.2 Deploy Production Stack

```bash
# Create secrets directory (if not using secrets manager)
mkdir -p secrets

# Deploy with production overlay
docker compose \
  -f docker-compose.base.yml \
  -f docker-compose.infra.yml \
  -f docker-compose.services.yml \
  -f docker-compose.prod.yml \
  --env-file .env.prod \
  up -d

# Verify deployment
docker compose ps
docker compose logs --tail=100
```

### 10.3 Keycloak Realm Update

```bash
# Apply production security settings to Keycloak realm
# Option 1: Via Admin Console - import production-security-overlay.json
# Option 2: Via Admin REST API

KEYCLOAK_URL="https://auth.yourdomain.com"
ADMIN_TOKEN="your_admin_token"

curl -X PUT "${KEYCLOAK_URL}/admin/realms/fortressbank-realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @keycloak/realms/production-security-overlay.json
```

### 10.4 Health Check Verification

```bash
# Check all services are healthy
for service in kong user-service account-service transaction-service; do
  echo "Checking ${service}..."
  docker compose exec ${service} wget --spider -q http://localhost:*/actuator/health && echo "OK" || echo "FAILED"
done
```

---

## 11. MOBILE APP SECURITY (January 2026)

### 11.1 Storage Security

- [ ] Expo SecureStore used for all sensitive data (not AsyncStorage)
- [ ] No hardcoded secrets in JavaScript bundle
- [ ] No sensitive data in Redux/state that persists to disk
- [ ] API keys loaded from environment/secure config

### 11.2 Cryptographic Security

- [ ] RSA-2048 minimum key size for device keypairs
- [ ] Private keys stored in platform secure enclave
- [ ] Biometric required to access signing operations
- [ ] No weak/predictable random number generation

### 11.3 Network Security

- [ ] SSL certificate pinning enabled for production
- [ ] No HTTP (plaintext) allowed, HTTPS only
- [ ] Certificate chain validated
- [ ] Network Security Config (Android) / ATS (iOS) properly configured

### 11.4 Authentication Security

- [ ] Biometric authentication uses hardware-backed keys
- [ ] Failed attempt tracking and lockout implemented
- [ ] Session timeout on app background
- [ ] Secure logout clears all sensitive data

### 11.5 Code Protection

- [ ] Hermes bytecode enabled (obfuscation)
- [ ] ProGuard/R8 enabled for Android
- [ ] Remove all console.log in production
- [ ] `__DEV__` checks for debug-only features
- [ ] Root/jailbreak detection implemented (recommended)
- [ ] Debugger detection implemented (recommended)

### 11.6 Screen Security

- [ ] Screenshot prevention on sensitive screens (recommended)
- [ ] Screen recording prevention (recommended)
- [ ] Background app snapshot redaction (recommended)

### 11.7 Input Validation

- [ ] Client-side validation matches backend validation
- [ ] XSS patterns rejected
- [ ] Input length limits enforced
- [ ] Clipboard auto-clear for sensitive data (recommended)

### 11.8 OWASP Mobile Top 10 Compliance

| OWASP ID | Risk | Status |
|----------|------|--------|
| M1 | Improper Platform Usage | ✅ Uses platform security APIs |
| M2 | Insecure Data Storage | ✅ SecureStore everywhere |
| M3 | Insecure Communication | ⚠️ Cert pinning recommended |
| M4 | Insecure Authentication | ✅ JWT + biometric gating |
| M5 | Insufficient Cryptography | ✅ RSA-2048, proper keys |
| M6 | Insecure Authorization | ✅ Backend-enforced |
| M7 | Client Code Quality | ✅ TypeScript, error handling |
| M8 | Code Tampering | ⚠️ Root detection recommended |
| M9 | Reverse Engineering | ⚠️ Obfuscation recommended |
| M10 | Extraneous Functionality | ✅ No debug in prod |

---

## SIGN-OFF

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Security Lead | | | |
| DevOps Lead | | | |
| Development Lead | | | |
| Operations Manager | | | |

---

## REVISION HISTORY

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-12-13 | Security Audit | Initial production checklist |

---

**IMPORTANT**: This checklist represents minimum security requirements. Additional measures may be required based on your specific regulatory environment and risk profile.

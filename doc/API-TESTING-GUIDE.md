# FortressBank API Testing Guide (No Frontend)

> Test all APIs using curl/httpie without a frontend. Perfect for development and CI/CD.

---

## Prerequisites

```bash
# Ensure services are running
docker-compose up -d

# Wait for services to be healthy (especially Keycloak)
docker-compose ps
```

---

## 1. Authentication

### Get Access Token

```bash
# Option A: Using curl
ACCESS_TOKEN=$(curl -s -X POST \
  "http://localhost:8888/realms/fortressbank-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=kong" \
  -d "client_secret=pmFStZwGO8sb0mBDkZmP5niE3wmEELqe" \
  -d "username=testuser" \
  -d "password=password" | jq -r '.access_token')

echo $ACCESS_TOKEN

# Option B: Using httpie
http --form POST http://localhost:8888/realms/fortressbank-realm/protocol/openid-connect/token \
  grant_type=password \
  client_id=kong \
  client_secret=pmFStZwGO8sb0mBDkZmP5niE3wmEELqe \
  username=testuser \
  password=password
```

### Verify Token

```bash
# Decode JWT (base64)
echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

---

## 2. User Service APIs

### Get Current User Profile

```bash
curl -s -X GET "http://localhost:8000/users/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

### Face Registration Status

```bash
curl -s -X GET "http://localhost:8000/users/face-id/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## 3. Account Service APIs

### List My Accounts

```bash
curl -s -X GET "http://localhost:8000/accounts" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

### Get Account Details

```bash
ACCOUNT_ID="your-account-id-here"
curl -s -X GET "http://localhost:8000/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

### Get Account Balance

```bash
curl -s -X GET "http://localhost:8000/accounts/$ACCOUNT_ID/balance" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## 4. Transaction Service APIs

### Create Transfer (Initiates Risk Assessment)

```bash
curl -s -X POST "http://localhost:8000/transactions/transfers" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "source-account-id",
    "toAccountId": "destination-account-id",
    "amount": 100000,
    "description": "Test transfer"
  }' | jq .
```

Response will include:
- `riskLevel`: LOW, MEDIUM, or HIGH
- `challengeType`: NONE, DEVICE_BIO, or FACE_VERIFY
- `challengeId`: If verification required

### Get Transaction Status

```bash
TRANSACTION_ID="transaction-uuid"
curl -s -X GET "http://localhost:8000/transactions/$TRANSACTION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## 5. Smart OTP APIs (New!)

### Check Smart OTP Status

```bash
curl -s -X GET "http://localhost:8000/smart-otp/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

### Register Device for Biometric Verification

```bash
# Generate a key pair first (in real mobile app, this is done in Secure Enclave)
# For testing, use OpenSSL:
openssl genrsa -out device_private.pem 2048
openssl rsa -in device_private.pem -pubout -out device_public.pem
PUBLIC_KEY=$(cat device_public.pem | tr -d '\n')

curl -s -X POST "http://localhost:8000/devices/register" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"deviceId\": \"device-$(uuidgen || date +%s)\",
    \"name\": \"My iPhone 15\",
    \"publicKeyPem\": \"$PUBLIC_KEY\"
  }" | jq .
```

### List My Devices

```bash
curl -s -X GET "http://localhost:8000/devices" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

### Verify Device Signature (for DEVICE_BIO challenge)

```bash
# Sign the challengeData with device private key
CHALLENGE_ID="challenge-id-from-transfer"
CHALLENGE_DATA="base64-challenge-from-response"
DEVICE_ID="your-device-id"

# Sign the challenge
SIGNATURE=$(echo -n "$CHALLENGE_DATA" | openssl dgst -sha256 -sign device_private.pem | base64 -w0)

curl -s -X POST "http://localhost:8000/smart-otp/verify-device" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"challengeId\": \"$CHALLENGE_ID\",
    \"deviceId\": \"$DEVICE_ID\",
    \"signatureBase64\": \"$SIGNATURE\"
  }" | jq .
```

---

## 6. TOTP (Google Authenticator) APIs

### Enroll TOTP

```bash
curl -s -X POST "http://localhost:8000/totp/enroll" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Response includes:
- `qrCodeDataUri`: Scan with Google Authenticator
- `secretKey`: Manual entry backup
- `recoveryCodes`: Save these securely!

### Confirm TOTP Enrollment

```bash
curl -s -X POST "http://localhost:8000/totp/confirm" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "123456"
  }' | jq .
```

### Check TOTP Status

```bash
curl -s -X GET "http://localhost:8000/totp/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## 7. Complete Transfer Flow (with Smart OTP)

### Step 1: Initiate Transfer

```bash
RESPONSE=$(curl -s -X POST "http://localhost:8000/transactions/transfers" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "acc-123",
    "toAccountId": "acc-456",
    "amount": 5000000,
    "description": "Large transfer requiring verification"
  }')

echo $RESPONSE | jq .

# Extract challenge info
CHALLENGE_TYPE=$(echo $RESPONSE | jq -r '.data.challengeType')
CHALLENGE_ID=$(echo $RESPONSE | jq -r '.data.challengeId')
CHALLENGE_DATA=$(echo $RESPONSE | jq -r '.data.challengeData')
```

### Step 2: Complete Verification (Based on Challenge Type)

```bash
if [ "$CHALLENGE_TYPE" = "DEVICE_BIO" ]; then
  # Sign and submit
  SIGNATURE=$(echo -n "$CHALLENGE_DATA" | openssl dgst -sha256 -sign device_private.pem | base64 -w0)
  
  curl -s -X POST "http://localhost:8000/smart-otp/verify-device" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"challengeId\": \"$CHALLENGE_ID\",
      \"deviceId\": \"$DEVICE_ID\",
      \"signatureBase64\": \"$SIGNATURE\"
    }" | jq .

elif [ "$CHALLENGE_TYPE" = "FACE_VERIFY" ]; then
  echo "Face verification required - use mobile app camera"
  
elif [ "$CHALLENGE_TYPE" = "NONE" ]; then
  echo "Low risk - transfer approved automatically!"
fi
```

---

## 8. Health Checks

```bash
# All services
for port in 4000 4001 4002 4003 4004 6000; do
  echo "Port $port: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:$port/actuator/health)"
done

# Kong Gateway
curl -s http://localhost:8000/status | jq .

# Keycloak
curl -s http://localhost:8888/health | jq .
```

---

## 9. PowerShell Equivalents (Windows)

```powershell
# Get token
$body = @{
    grant_type = "password"
    client_id = "kong"
    client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
    username = "testuser"
    password = "password"
}
$response = Invoke-RestMethod -Uri "http://localhost:8888/realms/fortressbank-realm/protocol/openid-connect/token" -Method POST -Body $body
$token = $response.access_token

# Use token
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:8000/accounts" -Headers $headers | ConvertTo-Json -Depth 10
```

---

## 10. Quick Test Script

Save as `quick-test.sh`:

```bash
#!/bin/bash
set -e

echo "🔐 Getting access token..."
ACCESS_TOKEN=$(curl -s -X POST \
  "http://localhost:8888/realms/fortressbank-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=kong&client_secret=pmFStZwGO8sb0mBDkZmP5niE3wmEELqe&username=testuser&password=password" \
  | jq -r '.access_token')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Failed to get token"
  exit 1
fi
echo "✅ Token acquired"

echo ""
echo "👤 User profile..."
curl -s "http://localhost:8000/users/me" -H "Authorization: Bearer $ACCESS_TOKEN" | jq .

echo ""
echo "💳 Accounts..."
curl -s "http://localhost:8000/accounts" -H "Authorization: Bearer $ACCESS_TOKEN" | jq .

echo ""
echo "📱 Smart OTP status..."
curl -s "http://localhost:8000/smart-otp/status" -H "Authorization: Bearer $ACCESS_TOKEN" | jq .

echo ""
echo "📋 Devices..."
curl -s "http://localhost:8000/devices" -H "Authorization: Bearer $ACCESS_TOKEN" | jq .

echo ""
echo "✅ All API tests completed!"
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `401 Unauthorized` | Token expired, get new one |
| `403 Forbidden` | User doesn't own resource (IDOR check working!) |
| `Connection refused` | Service not running, check `docker-compose ps` |
| `502 Bad Gateway` | Kong can't reach backend, check service health |
| `CORS error` | Add Origin header for browser testing |

---

*Last Updated: January 2026*

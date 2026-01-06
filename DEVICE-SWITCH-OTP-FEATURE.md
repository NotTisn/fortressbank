# Device Switch OTP Feature - Implementation Summary

## Overview

Implemented an OTP-based device switching feature for FortressBank's single-device authentication system. When a user is already logged in on Device A and attempts to login from Device B, the system now:

1. Detects the device conflict
2. Sends an OTP to the user's registered phone number
3. Prompts Device B user to enter the OTP
4. If OTP is verified successfully, logs out Device A and allows Device B to proceed

## Architecture

### Flow Diagram

```
Device B Login Attempt → SingleDeviceAuthenticator (detects conflict)
                              ↓
                    POST /api/users/device-switch/send-otp
                              ↓
                    DeviceSwitchService (generates 6-digit OTP)
                              ↓
                    Store in Redis (5min TTL, max 3 attempts)
                              ↓
                    Publish to RabbitMQ (device-switch.otp)
                              ↓
                    NotificationService (sends SMS via TextBee)
                              ↓
                    DeviceSwitchOtpAuthenticator (shows OTP form)
                              ↓
                    User enters OTP on Device B
                              ↓
                    POST /api/users/device-switch/verify-otp
                              ↓
                    If valid: Remove Device A sessions, allow Device B
                    If invalid: Show error, retry (up to 3 times)
```

## Files Created

### 1. Shared Kernel Constants

- **File**: [shared-kernel/src/main/java/com/uit/sharedkernel/constants/RedisKeyConstants.java](shared-kernel/src/main/java/com/uit/sharedkernel/constants/RedisKeyConstants.java)

  - Purpose: Define Redis key patterns for OTP storage
  - Key Constants:
    - `DEVICE_SWITCH_OTP_PREFIX = "device-switch:otp:"` - Stores OTP code
    - `DEVICE_SWITCH_ATTEMPTS_PREFIX = "device-switch:otp:attempts:"` - Tracks attempt count
    - `DEVICE_SWITCH_PENDING_PREFIX = "device-switch:pending:"` - Stores pending device ID
    - `OTP_EXPIRY_MINUTES = 5` - OTP validity period
    - `MAX_OTP_ATTEMPTS = 3` - Maximum verification attempts

- **File**: [shared-kernel/src/main/java/com/uit/sharedkernel/constants/RabbitMQConstants.java](shared-kernel/src/main/java/com/uit/sharedkernel/constants/RabbitMQConstants.java) (Modified)
  - Added:
    - `DEVICE_SWITCH_OTP_ROUTING_KEY = "device-switch.otp"`
    - `DEVICE_SWITCH_OTP_QUEUE = "notification.device-switch-otp.queue"`

### 2. Keycloak Extensions

- **File**: [keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/SingleDeviceAuthenticator.java](keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/SingleDeviceAuthenticator.java) (Modified)

  - Changes:
    - Added `CONFIG_ENABLE_OTP_CHALLENGE = "enableOtpChallenge"` config option
    - Added `PENDING_DEVICE_SWITCH_NOTE = "pendingDeviceSwitch"` session note
    - Modified `authenticate()` to call `triggerDeviceSwitchOtp()` when conflict detected and OTP enabled
    - New method: `triggerDeviceSwitchOtp(userId, newDeviceId)` - POSTs to user-service
    - Returns `otp_required` challenge to chain to next authenticator
    - Stores `pendingDeviceSwitch=true` and `newDeviceId` in session notes

- **File**: [keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/DeviceSwitchOtpAuthenticator.java](keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/DeviceSwitchOtpAuthenticator.java) (New)

  - Purpose: Handle OTP form display and verification
  - Key Methods:
    - `authenticate()`: Check for `pendingDeviceSwitch` flag, display OTP form ([device-switch-otp.ftl](keycloak/themes/fortressbank/login/device-switch-otp.ftl))
    - `action()`: Handle form submission, call `verifyDeviceSwitchOtp()`
    - `verifyDeviceSwitchOtp()`: POST to `/api/users/device-switch/verify-otp`
    - On success: Remove all old `UserSessionModel` instances, update device ID
    - On failure: Set error message, redisplay form

- **File**: [keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/DeviceSwitchOtpAuthenticatorFactory.java](keycloak-extensions/src/main/java/com/uit/fortressbank/keycloak/DeviceSwitchOtpAuthenticatorFactory.java) (New)

  - Purpose: SPI factory for Keycloak to discover the authenticator
  - Configuration:
    - ID: `device-switch-otp-authenticator`
    - Config property: `userServiceUrl` (default: `http://user-service:8081`)
    - Requirement: `CONDITIONAL` (only runs when pendingDeviceSwitch flag set)

- **File**: [keycloak-extensions/src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory](keycloak-extensions/src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory) (Modified)
  - Added: `com.uit.fortressbank.keycloak.DeviceSwitchOtpAuthenticatorFactory`

### 3. User Service

- **File**: [user-service/src/main/java/com/uit/userservice/dto/request/DeviceSwitchOtpRequest.java](user-service/src/main/java/com/uit/userservice/dto/request/DeviceSwitchOtpRequest.java) (New)

  - Fields: `userId`, `newDeviceId`

- **File**: [user-service/src/main/java/com/uit/userservice/dto/request/VerifyDeviceSwitchOtpRequest.java](user-service/src/main/java/com/uit/userservice/dto/request/VerifyDeviceSwitchOtpRequest.java) (New)

  - Fields: `userId`, `otpCode` (6-digit pattern validation)

- **File**: [user-service/src/main/java/com/uit/userservice/dto/response/DeviceSwitchOtpResponse.java](user-service/src/main/java/com/uit/userservice/dto/response/DeviceSwitchOtpResponse.java) (New)

  - Fields: `success` (boolean), `message` (String)

- **File**: [user-service/src/main/java/com/uit/userservice/service/DeviceSwitchService.java](user-service/src/main/java/com/uit/userservice/service/DeviceSwitchService.java) (New)

  - Interface defining:
    - `sendDeviceSwitchOtp(DeviceSwitchOtpRequest)`: Send OTP to user
    - `verifyDeviceSwitchOtp(VerifyDeviceSwitchOtpRequest)`: Verify submitted OTP

- **File**: [user-service/src/main/java/com/uit/userservice/service/DeviceSwitchServiceImpl.java](user-service/src/main/java/com/uit/userservice/service/DeviceSwitchServiceImpl.java) (New)

  - Key Features:
    - Generates 6-digit random OTP
    - Retrieves user phone number from database
    - Rate limiting: Max 3 OTP requests per 5 minutes
    - Stores OTP in Redis with 5-minute expiry
    - Publishes to RabbitMQ `TRANSACTION_EXCHANGE` with `DEVICE_SWITCH_OTP_ROUTING_KEY`
    - Verification checks Redis, validates attempts, cleans up keys on success

- **File**: [user-service/src/main/java/com/uit/userservice/controller/DeviceSwitchController.java](user-service/src/main/java/com/uit/userservice/controller/DeviceSwitchController.java) (New)
  - Base path: `/api/users/device-switch`
  - Endpoints:
    - `POST /send-otp`: Trigger OTP sending (called by Keycloak)
    - `POST /verify-otp`: Verify OTP code (called by Keycloak)
  - Returns `ApiResponse<DeviceSwitchOtpResponse>`

### 4. Notification Service

- **File**: [notification-service/src/main/java/com/uit/notificationservice/listener/NotificationListener.java](notification-service/src/main/java/com/uit/notificationservice/listener/NotificationListener.java) (Modified)
  - Added method: `handleDeviceSwitchOtpNotification(Map<String, Object>)`
    - Listens to: `DEVICE_SWITCH_OTP_QUEUE`
    - Extracts: `phoneNumber`, `otpCode`, `userId`
    - Calls: `notificationService.sendSmsOtp(phoneNumber, otpCode)` (TextBee API)

### 5. Keycloak Configuration

- **File**: [keycloak/realms/fortressbank-realm-export.json](keycloak/realms/fortressbank-realm-export.json) (Modified)

  - Added authenticator execution in "browser single log forms" flow:
    ```json
    {
      "authenticator": "device-switch-otp-authenticator",
      "requirement": "CONDITIONAL",
      "priority": 17
    }
    ```
  - Updated `single-device-authenticator-config`:
    ```json
    {
      "forceLogin": "false",
      "requireDeviceId": "false",
      "enableOtpChallenge": "true"
    }
    ```

- **File**: [keycloak/themes/fortressbank/login/device-switch-otp.ftl](keycloak/themes/fortressbank/login/device-switch-otp.ftl) (New)

  - FreeMarker template for OTP input form
  - Features:
    - 6-digit numeric input field
    - Auto-focus and autocomplete disabled
    - Pattern validation: `[0-9]{6}`
    - "Verify OTP" submit button
    - Error message display section

- **File**: [keycloak/themes/fortressbank/login/theme.properties](keycloak/themes/fortressbank/login/theme.properties) (New)
  - Sets parent theme: `parent=keycloak`

### 6. Docker Configuration

- **File**: [docker-compose.infra.yml](docker-compose.infra.yml) (Modified)
  - Added volume mount for Keycloak themes:
    ```yaml
    volumes:
      - ./keycloak/themes:/opt/keycloak/themes
    ```

## Configuration

### Redis Keys

- **OTP Storage**: `device-switch:otp:{userId}` → 6-digit code (5min TTL)
- **Attempt Counter**: `device-switch:otp:attempts:{userId}` → count (5min TTL, max 3)
- **Pending Device**: `device-switch:pending:{userId}` → newDeviceId (5min TTL)

### RabbitMQ Queue

- **Exchange**: `transaction-exchange`
- **Routing Key**: `device-switch.otp`
- **Queue**: `notification.device-switch-otp.queue`
- **Message Format**:
  ```json
  {
    "phoneNumber": "+84XXXXXXXXX",
    "otpCode": "123456",
    "userId": "uuid-here"
  }
  ```

### Keycloak Authenticator Config

```properties
forceLogin=false         # Don't automatically kick old session
requireDeviceId=false    # Device ID optional
enableOtpChallenge=true  # Enable OTP verification flow
```

## Security Considerations

1. **Rate Limiting**: Max 3 OTP requests per 5 minutes per user
2. **OTP Expiry**: 5-minute validity window
3. **Attempt Limiting**: Max 3 verification attempts before blocking
4. **SMS Security**: OTP sent only to verified phone number in database
5. **Session Management**: Old sessions completely removed on successful verification

## Testing Guide

### Prerequisites

- User account with registered phone number in database
- Two different browsers/devices (e.g., Chrome and Firefox)

### Test Steps

1. **Login on Device A**

   - Open Browser A (Chrome)
   - Navigate to `http://localhost:8888/realms/fortressbank-realm/account`
   - Login with test credentials
   - Verify successful login

2. **Trigger Device Conflict**

   - Open Browser B (Firefox)
   - Navigate to same URL
   - Login with SAME credentials
   - Expected: OTP form displayed instead of immediate block

3. **Check SMS/Logs**

   - Check notification-service logs for OTP message:
     ```bash
     docker logs notification-service
     ```
   - Look for: "Device Switch OTP SMS sent successfully to user: {userId}"
   - (In production, check actual SMS on phone)

4. **Verify OTP**

   - Enter 6-digit OTP in Device B form
   - Click "Verify OTP"
   - Expected: Device B logged in successfully

5. **Check Device A**
   - Switch back to Browser A
   - Try to access protected resource
   - Expected: Redirected to login (session kicked out)

### Test Cases

| Test Case       | Device A Status | Device B Action              | Expected Result                     |
| --------------- | --------------- | ---------------------------- | ----------------------------------- |
| Valid OTP       | Logged in       | Enter correct OTP            | Device A kicked, Device B logged in |
| Invalid OTP     | Logged in       | Enter wrong OTP 3 times      | Rate limit exceeded error           |
| Expired OTP     | Logged in       | Wait 6 minutes, enter OTP    | OTP expired error                   |
| No Phone Number | Logged in       | Device B login attempt       | "PHONE_NUMBER_NOT_FOUND" error      |
| Rate Limit      | Logged in       | Request OTP 4 times in 5 min | "TOO_MANY_ATTEMPTS" error           |

## API Endpoints

### User Service

#### Send Device Switch OTP

```http
POST http://user-service:8081/api/users/device-switch/send-otp
Content-Type: application/json

{
  "userId": "uuid-here",
  "newDeviceId": "browser-abc123"
}

Response:
{
  "code": 1000,
  "message": "Success",
  "data": {
    "success": true,
    "message": "OTP_SENT"
  }
}
```

#### Verify Device Switch OTP

```http
POST http://user-service:8081/api/users/device-switch/verify-otp
Content-Type: application/json

{
  "userId": "uuid-here",
  "otpCode": "123456"
}

Response (Success):
{
  "code": 1000,
  "message": "Success",
  "data": {
    "success": true,
    "message": "OTP_VERIFIED"
  }
}

Response (Error):
{
  "code": 400,
  "message": "OTP_INVALID",
  "data": {
    "success": false,
    "message": "OTP_INVALID"
  }
}
```

## Error Messages

| Code                   | Message | Description                           |
| ---------------------- | ------- | ------------------------------------- |
| OTP_SENT               | -       | OTP successfully sent to phone        |
| OTP_VERIFIED           | -       | OTP successfully verified             |
| PHONE_NUMBER_NOT_FOUND | -       | User has no registered phone number   |
| TOO_MANY_ATTEMPTS      | -       | Exceeded rate limit (3 requests/5min) |
| OTP_SEND_FAILED        | -       | RabbitMQ publish failure              |
| OTP_EXPIRED            | -       | OTP expired (5min TTL)                |
| OTP_INVALID            | -       | Incorrect OTP code                    |

## Deployment

### Build Commands

```bash
# Build shared-kernel (contains new constants)
cd shared-kernel
mvn clean install -DskipTests

# Build keycloak-extensions
cd ../keycloak-extensions
mvn clean package -DskipTests

# Build user-service
cd ../user-service
mvn clean package -DskipTests

# Build notification-service
cd ../notification-service
mvn clean package -DskipTests
```

### Docker Deployment

```bash
# Stop all services
docker compose down

# Start services (will copy new JARs)
docker compose up -d

# Check logs
docker logs fortressbank-keycloak
docker logs user-service
docker logs notification-service
```

### Verification

```bash
# Check Keycloak authenticators loaded
docker logs fortressbank-keycloak | grep "device-switch-otp-authenticator"

# Check user-service endpoints
curl http://localhost:4000/actuator/mappings | grep device-switch

# Check RabbitMQ queue created
curl -u guest:guest http://localhost:15672/api/queues/%2F/notification.device-switch-otp.queue
```

## Troubleshooting

### Issue: OTP form not showing

- **Check**: Keycloak realm config has `enableOtpChallenge=true`
- **Check**: `device-switch-otp-authenticator` execution added to flow
- **Fix**: Reimport realm or manually add in Keycloak admin console

### Issue: SMS not received

- **Check**: notification-service logs for RabbitMQ listener errors
- **Check**: User has valid phone number in database
- **Check**: TextBee API credentials configured correctly
- **Debug**: Check RabbitMQ management UI for messages in queue

### Issue: OTP verification fails

- **Check**: Redis keys exist: `redis-cli KEYS "device-switch:otp:*"`
- **Check**: OTP not expired (5min TTL)
- **Check**: Attempt counter not exceeded (max 3)
- **Debug**: Check user-service logs for verification attempts

### Issue: Device A not kicked out

- **Check**: DeviceSwitchOtpAuthenticator successfully calling `removeOldSessions()`
- **Check**: Keycloak user session database for duplicate sessions
- **Debug**: Add logging to `action()` method in authenticator

## Future Enhancements

1. **Multi-Device Support**: Allow N devices per user instead of single device
2. **Device Trust**: Remember trusted devices, skip OTP verification
3. **Email OTP**: Support OTP via email in addition to SMS
4. **Biometric Verification**: Use fingerprint/face ID as alternative to OTP
5. **Admin Dashboard**: View active devices per user, force logout capability
6. **Audit Trail**: Log all device switch attempts and OTP verifications
7. **Custom TTL**: Allow per-user OTP expiry configuration
8. **Push Notifications**: Use mobile app push instead of SMS

## Dependencies

### Maven Dependencies (Already Present)

- Spring Boot 3.2.0
- Spring Data Redis
- Spring AMQP (RabbitMQ)
- Keycloak 20.0 APIs
- PostgreSQL Driver
- Lombok

### External Services

- Redis 6 (OTP storage)
- RabbitMQ 3.12 (Message queue)
- PostgreSQL 16 (User data)
- TextBee API (SMS sending)

## Monitoring

### Key Metrics to Track

- OTP send success rate
- OTP verification success rate
- Average OTP delivery time
- Device switch attempts per hour
- Rate limit hits per hour
- Failed OTP attempts per user

### Log Patterns to Monitor

```
"Device Switch OTP sent successfully" → Success
"Failed to send Device Switch OTP" → SMS service failure
"Device switch OTP verified successfully" → User completed switch
"Rate limit exceeded" → Potential abuse
"OTP_EXPIRED" → User took too long to verify
```

## References

- Keycloak Authenticator SPI: https://www.keycloak.org/docs/20.0/server_development/#_auth_spi
- Spring AMQP: https://docs.spring.io/spring-amqp/reference/
- Redis TTL: https://redis.io/commands/expire/
- TextBee API: https://api.textbee.dev/docs

---

**Implementation Date**: January 6, 2026  
**Version**: 1.0.0  
**Status**: ✅ Deployed and Running

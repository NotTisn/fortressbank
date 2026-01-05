# User Preference API Documentation

## Base URL
```
/user-preferences
```

## Endpoints Overview

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/user-preferences/{userId}` | Get user preference by userId | User |
| GET | `/user-preferences` | Get all user preferences | Admin |
| POST | `/user-preferences/{userId}` | Create or update user preference | User |

---

## API Details

### 1. Get User Preference
Retrieves the notification preferences for a specific user.

**Endpoint:** `GET /user-preferences/{userId}`

**Path Parameters:**
- `userId` (string, required) - The unique identifier of the user

**Response:**
```json
{
  "success": true,
  "data": {
    "userId": "string",
    "phoneNumber": "string",
    "email": "string",
    "deviceToken": "string",
    "pushNotificationEnabled": boolean,
    "smsNotificationEnabled": boolean,
    "emailNotificationEnabled": boolean
  },
  "message": null,
  "timestamp": "2025-12-20T10:00:00Z"
}
```

**Status Codes:**
- `200 OK` - Successfully retrieved user preference
- `404 Not Found` - User preference not found

---

### 2. Get All User Preferences (Admin)
Retrieves all user preferences in the system. This is an admin-only endpoint.

**Endpoint:** `GET /user-preferences`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "userId": "string",
      "phoneNumber": "string",
      "email": "string",
      "deviceToken": "string",
      "pushNotificationEnabled": boolean,
      "smsNotificationEnabled": boolean,
      "emailNotificationEnabled": boolean
    }
  ],
  "message": null,
  "timestamp": "2025-12-20T10:00:00Z"
}
```

**Status Codes:**
- `200 OK` - Successfully retrieved all user preferences
- `403 Forbidden` - Insufficient permissions

---

### 3. Create or Update User Preference
Creates a new user preference or updates existing one if it already exists. This is an **upsert** operation using POST method.

**Endpoint:** `POST /user-preferences/{userId}`

**Path Parameters:**
- `userId` (string, required) - The unique identifier of the user

**Behavior:**
- If user preference doesn't exist: Creates new preference with provided values
  - Default values: `pushNotificationEnabled=true`, `smsNotificationEnabled=true`, `emailNotificationEnabled=true`
- If user preference exists: Updates only the provided fields (partial update)

**Request Body:**
```json
{
  "phoneNumber": "string (optional)",
  "email": "string (optional, must be valid email format)",
  "deviceToken": "string (optional)",
  "pushNotificationEnabled": boolean (optional),
  "smsNotificationEnabled": boolean (optional),
  "emailNotificationEnabled": boolean (optional)
}
```

**Request Example:**
```json
{
  "phoneNumber": "+84901234567",
  "email": "user@example.com",
  "deviceToken": "fcm-token-123",
  "pushNotificationEnabled": true,
  "smsNotificationEnabled": true,
  "emailNotificationEnabled": false
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "userId": "string",
    "phoneNumber": "string",
    "email": "string",
    "deviceToken": "string",
    "pushNotificationEnabled": boolean,
    "smsNotificationEnabled": boolean,
    "emailNotificationEnabled": boolean
  },
  "message": null,
  "timestamp": "2025-12-20T10:00:00Z"
}
```

**Status Codes:**
- `201 Created` - User preference successfully created or updated
- `400 Bad Request` - Invalid request body or validation error

---

## Data Models

### UserPreferenceRequest
```java
{
  "phoneNumber": "string (optional)",
  "email": "string (optional, valid email format required)",
  "deviceToken": "string (optional)",
  "pushNotificationEnabled": "boolean (optional)",
  "smsNotificationEnabled": "boolean (optional)",
  "emailNotificationEnabled": "boolean (optional)"
}
```

### UserPreferenceResponse
```java
{
  "userId": "string",
  "phoneNumber": "string",
  "email": "string",
  "deviceToken": "string",
  "pushNotificationEnabled": "boolean",
  "smsNotificationEnabled": "boolean",
  "emailNotificationEnabled": "boolean"
}
```

---

## Validation Rules

### Email
- Must be a valid email format
- Validation annotation: `@Email(message = "Invalid email format")`

### Request Body
- All request bodies must be valid JSON
- All fields in UserPreferenceRequest are optional
- Validation is performed using `@Valid` annotation

---

## Error Responses

All error responses follow this format:
```json
{
  "success": false,
  "data": null,
  "message": "Error message description",
  "timestamp": "2025-12-20T10:00:00Z"
}
```

Common error scenarios:
- **400 Bad Request**: Invalid email format, malformed JSON
- **404 Not Found**: User preference doesn't exist (only for GET single endpoint)
- **500 Internal Server Error**: Unexpected server error

---

## Important Notes

### Upsert Behavior
The POST endpoint implements **upsert** (create or update) logic:
- **If user preference doesn't exist**: Creates new with provided values + defaults
- **If user preference exists**: Updates only the provided fields (partial update)
- **No conflict errors**: The endpoint handles existing preferences gracefully

### Default Values
When creating a new user preference (first time):
```json
{
  "pushNotificationEnabled": true,
  "smsNotificationEnabled": true,
  "emailNotificationEnabled": true
}
```

### Partial Updates
All fields in the request body are optional. Only provided fields will be updated:
```json
// Example: Only update email, other fields remain unchanged
{
  "email": "newemail@example.com"
}
```

### General Notes
1. All endpoints use the `ApiResponse` wrapper for consistent response structure
2. The `userId` path parameter is the primary identifier for user preferences
3. The GET all preferences endpoint should be restricted to admin users
4. Logging is implemented for all operations for audit purposes
5. Email validation is enforced using `@Email` annotation
6. Single POST endpoint handles both create and update operations (upsert pattern)

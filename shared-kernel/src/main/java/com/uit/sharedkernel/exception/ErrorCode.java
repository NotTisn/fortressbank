package com.uit.sharedkernel.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR), NOT_FOUND_EXCEPTION(404, "Card not found", HttpStatus.NOT_FOUND), BLOCK_CARD_CONFLICT(409, "Card is already blocked", HttpStatus.CONFLICT), ACTIVATE_CARD_CONFLICT(409, "Card is already activated", HttpStatus.CONFLICT), USERNAME_EXISTS(409, "Username already exists", HttpStatus.CONFLICT), EMAIL_EXISTS(409, "Email already exists", HttpStatus.CONFLICT), ROLE_EXISTS(409, "Role already exists", HttpStatus.CONFLICT),

    /* Account */
    BAD_REQUEST(400, "Bad request", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(400, "Invalid credentials", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND(404, "Account not found", HttpStatus.NOT_FOUND), ACCOUNT_STATUS_CONFLICT(409, "Invalid account status transition", HttpStatus.CONFLICT), ACCOUNT_CLOSE_NONZERO_BALANCE(409, "Cannot close account with non-zero balance", HttpStatus.CONFLICT), ACCOUNT_INVALID_BALANCE(400, "Invalid balance amount", HttpStatus.BAD_REQUEST), INSUFFICIENT_FUNDS(400, "Insufficient funds", HttpStatus.BAD_REQUEST), FORBIDDEN(403, "You do not have permission to perform this action", HttpStatus.FORBIDDEN), INVALID_OTP(400, "Invalid OTP code", HttpStatus.BAD_REQUEST), RISK_ASSESSMENT_FAILED(500, "Risk assessment failed", HttpStatus.INTERNAL_SERVER_ERROR), NOTIFICATION_SERVICE_FAILED(500, "Notification service failed", HttpStatus.INTERNAL_SERVER_ERROR), REDIS_CONNECTION_FAILED(500, "Redis connection failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(503, "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR(500, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    /* User */
    USER_CREATION_FAILED(500, "User creation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_ALREADY_EXISTS(409, "User already existed", HttpStatus.CONFLICT),
    UNAUTHORIZED(401, "Unauthorized access", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(404, "User not found", HttpStatus.NOT_FOUND),
    PHONE_NUMBER_NOT_FOUND(404, "Phone number not found for user", HttpStatus.NOT_FOUND),
    FACE_VERIFICATION_FAILED(400, "Face verification failed", HttpStatus.BAD_REQUEST),
    INVALID_FACE_DATA(400, "Invalid face data provided", HttpStatus.BAD_REQUEST),

    /* Transaction */
    TRANSACTION_NOT_FOUND(404, "Transaction not found", HttpStatus.NOT_FOUND),
    TRANSACTION_STATUS_CONFLICT(409, "Transaction is not in a valid state for this operation", HttpStatus.CONFLICT),

    /* OTP */
    OTP_NOT_FOUND(404, "OTP data not found or already used", HttpStatus.NOT_FOUND),
    OTP_RESEND_COOLDOWN(429, "Resend OTP cooldown period has not passed", HttpStatus.TOO_MANY_REQUESTS),

    /* TOTP (Smart OTP) */
    TOTP_NOT_ENROLLED(404, "Smart OTP not enrolled", HttpStatus.NOT_FOUND),
    TOTP_ALREADY_ACTIVE(409, "Smart OTP is already active", HttpStatus.CONFLICT),
    TOTP_LOCKED_OUT(429, "Smart OTP temporarily locked due to failed attempts", HttpStatus.TOO_MANY_REQUESTS),

    /* Forgot Password */
    OTP_EXPIRED_OR_NOT_FOUND(400, "OTP has expired or was not found", HttpStatus.BAD_REQUEST),
    MAX_OTP_ATTEMPTS_EXCEEDED(400, "Maximum OTP verification attempts exceeded", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_EXPIRED(400, "Verification token has expired", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_TOKEN(400, "Invalid verification token", HttpStatus.BAD_REQUEST),

    /* Notification */
    USER_PREFERENCE_NOT_FOUND(404, "User preference not found", HttpStatus.NOT_FOUND),
    NOTIF_NOT_FOUND(404, "Notification not found", HttpStatus.NOT_FOUND);

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private int code;
    private String message;
    private HttpStatusCode statusCode;
}


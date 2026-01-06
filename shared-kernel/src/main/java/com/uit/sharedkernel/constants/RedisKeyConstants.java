package com.uit.sharedkernel.constants;

public class RedisKeyConstants {
    // OTP Keys
    public static final String REGISTRATION_OTP_PREFIX = "registration:otp:";
    public static final String REGISTRATION_OTP_VERIFIED_PREFIX = "registration:otp:verified:";
    public static final String FORGOT_PASSWORD_OTP_PREFIX = "forgot-password:otp:";
    public static final String FORGOT_PASSWORD_OTP_ATTEMPTS_PREFIX = "forgot-password:otp:attempts:";
    
    // Device Switch OTP Keys
    public static final String DEVICE_SWITCH_OTP_PREFIX = "device-switch:otp:";
    public static final String DEVICE_SWITCH_ATTEMPTS_PREFIX = "device-switch:otp:attempts:";
    public static final String DEVICE_SWITCH_PENDING_PREFIX = "device-switch:pending:";
    
    // OTP Settings
    public static final int OTP_EXPIRY_MINUTES = 5;
    public static final int MAX_OTP_ATTEMPTS = 3;
    public static final int PENDING_DEVICE_SWITCH_EXPIRY_MINUTES = 10;

    private RedisKeyConstants() {
        // Prevent instantiation
    }
}

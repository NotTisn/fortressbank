package com.uit.sharedkernel.constants;

public class RabbitMQConstants {
    // Exchanges
    public static final String TRANSACTION_EXCHANGE = "transaction-exchange";
    public static final String AUDIT_EXCHANGE = "audit.exchange";
    
    // Routing Keys
    public static final String TRANSACTION_CREATED = "transaction.created";
    public static final String MONEY_TRANSFERRED = "money.transferred";
    public static final String AUDIT_LOG = "audit.log";
    public static final String OTP_ROUTING_KEY = "otp.generated";
    public static final String TRANSACTION_SUCCESS = "transaction.success";
    public static final String FORGOT_PASSWORD_OTP_ROUTING_KEY = "forgot-password.otp";

    // Queues
    public static final String TRANSACTION_QUEUE = "transaction-queue";
    public static final String AUDIT_QUEUE = "audit.queue";
    public static final String OTP_QUEUE = "notification.otp.queue";
    public static final String FORGOT_PASSWORD_OTP_QUEUE = "notification.forgot-password-otp.queue";

    public static final String INTERNAL_EXCHANGE = "internal.exchange";
    public static final String USER_CREATED_QUEUE = "user.created.queue";
    public static final String USER_CREATED_ROUTING_KEY = "user.created";

    private RabbitMQConstants() {
        // Prevent instantiation
    }
}

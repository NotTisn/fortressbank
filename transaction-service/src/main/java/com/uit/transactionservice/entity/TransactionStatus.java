package com.uit.transactionservice.entity;

public enum TransactionStatus {
    PENDING_OTP,   // Đang chờ xác thực OTP
    PENDING_FACE_AUTH, // Đang chờ xác thực khuôn mặt (FaceID)
    PENDING,       // Đang chờ xử lý (sau khi OTP verified)
    PROCESSING,    // Đang xử lý
    SUCCESS,       // Thành công (hoàn thành)
    COMPLETED,     // Hoàn thành (legacy, same as SUCCESS)
    FAILED,        // Thất bại
    CANCELLED,     // Đã hủy
    OTP_EXPIRED,   // OTP hết hạn
    ROLLBACK_FAILED, // Rollback thất bại - cần xử lý thủ công
    ROLLBACK_COMPLETED // Rollback hoàn thành
}

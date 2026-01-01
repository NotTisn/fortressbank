# Hướng Dẫn Tích Hợp và Chạy Stripe cho FortressBank

Tài liệu này hướng dẫn chi tiết cách cấu hình và chạy tích hợp Stripe (cổng thanh toán) cho **Transaction Service** trong dự án FortressBank.

## 1. Tổng Quan

Hệ thống FortressBank sử dụng Stripe để:
1.  Thực hiện chuyển khoản (Transfer) giữa các tài khoản kết nối (Connected Accounts).
2.  Xử lý Webhooks để cập nhật trạng thái giao dịch (Completed/Failed).

## 2. Chuẩn Bị (Prerequisites)

Bạn cần có tài khoản **Stripe Developer** (chế độ Test Mode).

### 2.1. Lấy API Keys
Truy cập [Stripe Dashboard > Developers > API Keys](https://dashboard.stripe.com/test/apikeys):
*   **Secret Key**: `sk_test_...` (Dùng cho `STRIPE_SECRET_KEY`)
*   **Publishable Key**: `pk_test_...` (Không bắt buộc cho backend này, nhưng tốt để lưu)

### 2.2. Lấy Webhook Secret (Cho Localhost)
Bạn cần cài đặt **Stripe CLI** để nhận webhook tại local.
1.  Tải và cài đặt [Stripe CLI](https://stripe.com/docs/stripe-cli).
2.  Đăng nhập: `stripe login`
3.  Lắng nghe webhook và lấy secret:
    ```bash
    stripe listen --forward-to localhost:4004/api/webhook/stripe
    ```
    *   Output sẽ chứa: `Your webhook signing secret is whsec_...`
    *   Copy chuỗi `whsec_...` này (Dùng cho `STRIPE_WEBHOOK_SECRET`).
    *   **Lưu ý:** Giữ terminal này chạy để nhận sự kiện từ Stripe.

### 2.3. Lấy Connected Account ID (Tùy chọn/Mặc định)
Để test chuyển tiền, bạn cần một tài khoản đích (Connected Account) trên Stripe.
*   Bạn có thể tạo bằng script `test.ps1` hoặc qua Dashboard.
*   ID có dạng: `acct_...` (Dùng cho `STRIPE_CONNECT_ACCOUNT_ID` nếu cần default).

## 3. Cấu Hình (Configuration)

Bạn cần cung cấp các biến môi trường cho `transaction-service`.

### Cách 1: Cập nhật `docker-compose.services.yml` (Khuyên dùng)
Mở file `docker-compose.services.yml`, tìm service `transaction-service` và thêm vào phần `environment`:

```yaml
  transaction-service:
    # ... các cấu hình khác
    environment:
      # ...
      # Thêm các dòng dưới đây:
      STRIPE_SECRET_KEY: sk_test_YOUR_SECRET_KEY_HERE
      STRIPE_WEBHOOK_SECRET: whsec_YOUR_WEBHOOK_SECRET_HERE
      STRIPE_CONNECT_ACCOUNT_ID: acct_YOUR_CONNECTED_ACCOUNT_ID_HERE
      STRIPE_CURRENCY: usd
```

### Cách 2: Chạy trực tiếp với IntelliJ/Maven
Nếu chạy service bằng IntelliJ hoặc Command Line, hãy set biến môi trường trong Run Configuration:
*   `STRIPE_SECRET_KEY=sk_test_...`
*   `STRIPE_WEBHOOK_SECRET=whsec_...`
*   `STRIPE_CONNECT_ACCOUNT_ID=acct_...`

## 4. Chạy Ứng Dụng

1.  **Khởi động Stack:**
    ```bash
    docker-compose -f docker-compose.base.yml -f docker-compose.infra.yml -f docker-compose.services.yml up -d transaction-service
    ```
    *(Hoặc restart lại toàn bộ stack nếu cần)*

2.  **Kiểm tra Logs:**
    ```bash
    docker logs -f transaction-service
    ```
    Nếu cấu hình đúng, bạn sẽ thấy log: `Stripe API initialized successfully`.

3.  **Đảm bảo Stripe CLI đang chạy:**
    Terminal chạy lệnh `stripe listen ...` phải luôn mở.

## 5. Kiểm Thử (Testing)

### 5.1. Tạo Giao Dịch Chuyển Khoản
Sử dụng Postman hoặc Curl để gọi API tạo giao dịch chuyển khoản ra ngoài (External Transfer).

**Endpoint:** `POST http://localhost:8000/transactions/transfer` (hoặc port 4004 nếu gọi trực tiếp)
**Body:**
```json
{
  "fromAccountId": "ACC_SOURCE",
  "toAccountId": "ACC_DESTINATION", 
  "amount": 100,
  "type": "EXTERNAL",
  "paymentGateway": "STRIPE",
  "destinationBankCode": "Stripe" 
}
```

### 5.2. Luồng Xử Lý
1.  **Gửi Request:** `transaction-service` gọi Stripe API để tạo Transfer.
2.  **Trạng thái đầu:** Transaction có trạng thái `PENDING` hoặc `PROCESSING`.
3.  **Stripe xử lý:** Stripe thực hiện chuyển tiền.
4.  **Webhook:** Stripe gửi sự kiện `transfer.created` hoặc `transfer.updated` về CLI.
5.  **Forward:** CLI đẩy về `localhost:4004/api/webhook/stripe`.
6.  **Cập nhật:** `transaction-service` nhận webhook, verify chữ ký, và cập nhật trạng thái transaction thành `COMPLETED` (hoặc `FAILED`).

### 5.3. Debug Lỗi
*   **Lỗi 500/400 từ API:** Kiểm tra `STRIPE_SECRET_KEY` đã đúng chưa.
*   **Webhook không nhận được:** Kiểm tra `stripe listen` có đang chạy và đúng port `4004` không.
*   **Lỗi Signature Verification:** Kiểm tra `STRIPE_WEBHOOK_SECRET` có khớp với secret mà `stripe listen` hiển thị không.

## 6. Các Lệnh Hữu Ích Khác
*   **Trigger sự kiện test từ CLI:**
    ```bash
    stripe trigger transfer.created
    ```
*   **Xem danh sách transfers:**
    ```bash
    stripe transfers list
    ```

# Integration Tests với Testcontainers

## Tổng quan

Dự án này đã được triển khai integration tests sử dụng **Testcontainers** cho các microservices sau:

- **user-service**: Quản lý thông tin người dùng
- **account-service**: Quản lý tài khoản ngân hàng
- **transaction-service**: Xử lý giao dịch chuyển tiền

## Công nghệ sử dụng

- **Testcontainers**: Framework để chạy Docker containers trong tests
- **PostgreSQL Container**: Database chính thay thế H2
- **Redis Container**: Cache và session management
- **RabbitMQ Container**: Message broker cho event-driven architecture
- **JUnit 5**: Testing framework
- **Spring Boot Test**: Integration test support

## Cấu trúc Tests

### User Service

#### BaseIntegrationTest.java
- Base class cho tất cả integration tests
- Khởi tạo và cấu hình các containers (PostgreSQL, Redis, RabbitMQ)
- Tự động inject configuration vào Spring context

#### UserControllerIntegrationTest.java
- Test các REST endpoints của UserController
- Test authentication và authorization
- Test validation và error handling
- **Các test cases:**
  - `GET /users/me` - Lấy thông tin user hiện tại
  - `PATCH /users/me` - Cập nhật thông tin user
  - Validation: phone number, email format
  - Security: unauthorized access

#### UserServiceIntegrationTest.java
- Test business logic trong UserService
- Test database operations với PostgreSQL
- **Các test cases:**
  - Lấy user theo JWT token
  - Cập nhật thông tin user
  - Handle user không tồn tại
  - Concurrent updates

### Account Service

#### BaseIntegrationTest.java
- Tương tự user-service với cấu hình phù hợp

#### AccountControllerIntegrationTest.java
- Test REST API của AccountController
- **Các test cases:**
  - `GET /accounts/my-accounts` - Lấy danh sách tài khoản
  - `POST /accounts/internal/{accountId}/debit` - Trừ tiền
  - `POST /accounts/internal/{accountId}/credit` - Cộng tiền
  - Insufficient balance handling
  - Account not found scenarios

#### AccountServiceTestcontainersTest.java
- Test business logic phức tạp
- **Các test cases:**
  - Debit/Credit operations
  - Pessimistic locking (concurrent transactions)
  - Balance validation
  - Account status management
  - Multi-threaded scenarios

### Transaction Service

#### BaseIntegrationTest.java
- Container setup cho transaction service

#### TransactionServiceIntegrationTest.java
- Test business logic của transactions
- **Các test cases:**
  - Tạo transfer với OTP
  - Query transactions theo sender/receiver
  - Filter theo status
  - Transaction với fees
  - Different transaction types (TRANSFER, DEPOSIT, WITHDRAWAL)

#### TransactionControllerIntegrationTest.java
- Test REST endpoints
- **Các test cases:**
  - `POST /transactions/transfers` - Tạo giao dịch
  - `GET /transactions/my-transactions` - Lấy lịch sử
  - `GET /transactions/{id}` - Chi tiết giao dịch
  - Validation: amount, account numbers
  - Security và error handling

## Yêu cầu hệ thống

1. **Docker Desktop** phải được cài đặt và đang chạy
2. **Java 21** 
3. **Maven 3.8+**
4. Ít nhất **4GB RAM** cho Docker containers

## Cách chạy tests

### Chạy tất cả integration tests cho một service:

```bash
# User Service
mvn test -pl user-service

# Account Service  
mvn test -pl account-service

# Transaction Service
mvn test -pl transaction-service
```

### Chạy một test class cụ thể:

```bash
# Ví dụ: Chỉ chạy UserControllerIntegrationTest
mvn test -pl user-service -Dtest=UserControllerIntegrationTest

# Chỉ chạy AccountServiceTestcontainersTest
mvn test -pl account-service -Dtest=AccountServiceTestcontainersTest
```

### Chạy một test method cụ thể:

```bash
mvn test -pl transaction-service -Dtest=TransactionControllerIntegrationTest#testCreateTransfer_Success
```

### Chạy tất cả tests trong project:

```bash
mvn clean test
```

## Lưu ý quan trọng

### 1. Container Reuse
Tests sử dụng `.withReuse(true)` để tái sử dụng containers giữa các test runs, giúp:
- Giảm thời gian khởi động
- Tiết kiệm tài nguyên
- Faster feedback loop

### 2. Test Isolation
Mỗi test method có `@BeforeEach` để:
- Clear database (`deleteAll()`)
- Reset test data
- Đảm bảo tests độc lập với nhau

### 3. Mock External Services
Các external services được mock:
- **Keycloak**: JwtDecoder được mock
- **Stripe API**: StripeTransferService được mock
- **User/Account Service clients**: Feign clients được mock
- **OTP Service**: SMS/Email sending được mock

### 4. Database Migrations
- Sử dụng **Flyway** để manage schema
- Tests chạy với real PostgreSQL, không phải H2
- Schema được validate bởi Hibernate (`ddl-auto: validate`)

### 5. Active Profile
Tests chạy với profile `test`:
- File config: `application-test.yml`
- Disable cloud config, eureka
- Dynamic properties từ Testcontainers

## Troubleshooting

### Docker không chạy
```
Error: Could not find a valid Docker environment
```
**Giải pháp**: Khởi động Docker Desktop

### Port conflicts
```
Error: Port 5432 is already in use
```
**Giải pháp**: 
- Stop các PostgreSQL instances đang chạy
- Hoặc dùng container reuse

### Memory issues
```
Error: Container failed to start - Out of memory
```
**Giải pháp**: 
- Tăng memory cho Docker (Settings > Resources)
- Close các applications không cần thiết

### Flyway migration errors
```
Error: Migration checksum mismatch
```
**Giải pháp**:
```bash
# Clean và rebuild
mvn clean install -DskipTests
mvn test
```

## Best Practices đã áp dụng

1. **Test Pyramid**: Focus vào integration tests cho business logic phức tạp
2. **Test Containers**: Sử dụng real services thay vì mocks
3. **Isolation**: Mỗi test độc lập, không phụ thuộc thứ tự
4. **Fast Feedback**: Container reuse, parallel execution
5. **Readability**: Clear test names với @DisplayName
6. **Assertions**: Sử dụng AssertJ cho expressive assertions
7. **Given-When-Then**: Cấu trúc test rõ ràng

## Performance

### Thời gian chạy ước tính:

- **User Service**: ~15-20 seconds (2 test classes)
- **Account Service**: ~20-25 seconds (2 test classes)
- **Transaction Service**: ~25-30 seconds (2 test classes)

**First run** (download images): ~2-3 phút
**Subsequent runs** (with reuse): ~1 phút

## Test Coverage

Các integration tests này cover:

✅ REST API endpoints
✅ Business logic trong services
✅ Database operations (CRUD)
✅ Transaction management
✅ Pessimistic locking
✅ Concurrent scenarios
✅ Validation logic
✅ Error handling
✅ Security (authentication/authorization)
✅ Integration với Redis
✅ Integration với RabbitMQ

## Tài liệu tham khảo

- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

## Liên hệ

Nếu có vấn đề với integration tests, hãy tạo issue hoặc liên hệ team.

# BẢN TỰ ĐÁNH GIÁ CÁ NHÂN - FORTRESSBANK PROJECT

**Họ và tên:** [Tên của bạn]  
**MSSV:** [MSSV của bạn]  
**Ngày:** 05/01/2026  
**Dự án:** FortressBank - Microservices Banking Application

---

## 1. PHẦN CÔNG VIỆC CÁ NHÂN ĐÃ TRỰC TIẾP THỰC HIỆN

### 1.1. Cấu hình Docker Compose cho Notification Service

Tôi đã đảm nhận việc cấu hình Docker Compose cho **Notification Service**, bao gồm cả infrastructure và service configuration:

#### **Infrastructure Configuration (docker-compose.infra.yml)**
- Cấu hình PostgreSQL database cho notification service:
  - Container name: `notification-service-db`
  - Port mapping: `5437:5432` (đã kiểm tra và giải quyết port conflict)
  - Database name: `notificationdb`
  - Volume persistence: `notificationdb_data`
  - Health check configuration để đảm bảo database sẵn sàng trước khi service khởi động

#### **Service Configuration (docker-compose.services.yml)**
- Cấu hình Notification Service container với các environment variables:
  - Database connection (SPRING_DATASOURCE_URL, USERNAME, PASSWORD)
  - Spring profiles và Config Server integration
  - RabbitMQ configuration: `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`
  - Email service configuration (SendGrid): `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
  - Service dependencies với `depends_on` và health checks
- Port mapping: `4002:4002`
- Network configuration: `fortressbank-network`

### 1.2. Build và Deploy trong quá trình Development

Trong suốt quá trình phát triển, tôi đã thực hiện:

**Build Process:**
```bash
# Maven build cho notification service
mvn clean install -DskipTests

# Docker build cho notification service
docker compose build notification-service
```

**Deploy & Testing:**
```bash
# Deploy toàn bộ hệ thống
docker compose up -d

# Monitor logs để debug
docker compose logs -f notification-service

# Restart service khi có thay đổi
docker compose restart notification-service
```

Tôi đã thực hiện build và deploy nhiều lần để test các chức năng của notification service và đảm bảo tích hợp tốt với các service khác.

### 1.3. Cấu hình API Gateway (Kong) cho Notification Service

Tôi đã cấu hình routing và security policy cho Notification Service trong **kong.yml**:

- **Route configuration**: Định nghĩa API routes cho notification service
- **Path mapping**: `/notifications/*` routes đến notification-service:4002
- **Authentication**: Tích hợp OIDC plugin để bảo vệ endpoints
- **Strip path configuration**: Đảm bảo routing chính xác giữa Kong và backend service

---

## 2. PHẦN KIẾN THỨC CÁ NHÂN NẮM RÕ NHẤT

### 2.1. Docker Image và Container

Tôi hiểu rõ sự khác biệt và cách sử dụng Docker trong microservices:

- **Docker Image**: Template chỉ đọc chứa application và dependencies
  - Build từ Dockerfile với các instructions (FROM, COPY, RUN, ENTRYPOINT)
  - Tag và version management
  - Layer caching để optimize build time

- **Container**: Instance runtime của image
  - Isolated environment với own filesystem, networking
  - Lifecycle management: start, stop, restart, remove
  - Container orchestration với Docker Compose

- **Practical Understanding**: 
  - Sử dụng `eclipse-temurin:21-jre` base image cho Spring Boot services
  - Multi-stage builds để tối ưu image size
  - Volume mounting để persist database data

### 2.2. Microservices Communication với RabbitMQ

Tôi nắm vững cách các microservices giao tiếp với nhau, đặc biệt là message-driven architecture:

**Event-Driven Architecture:**
- **Producer Services** (Account Service, Transaction Service) publish events đến RabbitMQ
- **Consumer Service** (Notification Service) subscribe và xử lý events
- **Message Queue Benefits**:
  - Asynchronous communication: Services không block nhau
  - Decoupling: Services độc lập, dễ scale
  - Reliability: Message persistence, retry mechanism

**RabbitMQ Integration trong Notification Service:**
- **Exchange Types**: Sử dụng `internal.exchange` và `transaction-exchange` để route messages
- **Queue Configuration**:
  - `notification.otp.queue`: Xử lý OTP messages
  - `notification.forgot-password-otp.queue`: Xử lý OTP quên mật khẩu
  - `notification.registration-otp.queue`: Xử lý OTP đăng ký
  - `transaction-queue`: Xử lý thông báo giao dịch
- **Message Format**: JSON serialization với Spring AMQP
- **Error Handling**: Dead Letter Queue (DLQ) cho failed messages

**Concrete Example:**
```
Account Service → RabbitMQ Exchange → Notification Queue → Notification Service → Send Email/SMS
```

### 2.3. API Gateway Pattern với Kong

Tôi hiểu rõ vai trò và cách cấu hình Kong API Gateway:

- **Single Entry Point**: Tất cả client requests đi qua Kong (port 8000)
- **Service Discovery**: Kong route requests đến đúng backend service
- **Security Layer**: 
  - OIDC plugin tích hợp với Keycloak
  - JWT validation tại gateway level
  - Rate limiting và throttling
- **Load Balancing**: Distribute traffic khi scale services
- **Declarative Configuration**: Infrastructure as Code với `kong.yml`

---

## 3. KHÓ KHĂN KỸ THUẬT VÀ CÁCH GIẢI QUYẾT

### 3.1. Debug RabbitMQ Communication giữa Services

**Vấn đề:**
Trong quá trình phát triển, tôi gặp nhiều khó khăn khi debug giao tiếp giữa Notification Service và các services khác thông qua RabbitMQ:

- Messages không được consume từ queue `notification.otp.queue`
- Serialization/deserialization errors với JSON format
- Connection timeout giữa services
- Message routing không đúng exchange/queue

**Cách giải quyết:**

1. **RabbitMQ Management UI**: 
   - Access RabbitMQ Management Console (port 15672)
   - Monitor queues (`notification.otp.queue`, `transaction-queue`), exchanges, bindings để xem message flow
   - Check message rates và identify bottlenecks

2. **Logging và Tracing**:
   ```java
   // Thêm detailed logging trong message listeners
   @RabbitListener(queues = RabbitMQConstants.OTP_QUEUE)
   public void handleNotification(NotificationEvent event) {
       log.info("Received notification event: {}", event);
       // Process event...
   }
   ```

3. **Docker Network Debug**:
   ```bash
   # Verify services có thể communicate
   docker exec notification-service ping rabbitmq
   
   # Check RabbitMQ logs
   docker compose logs rabbitmq
   ```

4. **Configuration Validation**:
   - Verify queue names, exchange names match giữa producer và consumer
   - Check RabbitMQ connection string và credentials
   - Ensure `spring.rabbitmq.host=rabbitmq` đúng với container name

**Kết quả**: Sau khi debug kỹ càng, tôi đã hiểu sâu về message-driven architecture và có thể troubleshoot problems nhanh chóng.

### 3.2. Port Conflict Resolution

**Vấn đề:**
Khi cấu hình infrastructure cho Notification Service, tôi gặp port conflict:

- PostgreSQL ports đã được sử dụng bởi các services khác (5432-5436)
- Notification Service port cần tránh conflict với existing services

**Cách giải quyết:**

1. **Port Mapping Analysis**:
   ```bash
   # Check existing port assignments
   docker compose ps
   
   # View all port mappings
   netstat -ano | findstr :543
   ```

2. **Systematic Port Assignment**:
   - User Service DB: `5433:5432`
   - Account Service DB: `5434:5432`
   - Transaction Service DB: `5435:5432`
   - Reference Service DB: `5436:5432`
   - **Notification Service DB: `5437:5432`** ← Chọn port tiếp theo available

3. **Service Port Selection**:
   - Verify service port pattern: 4000, 4001, 4002, ...
   - Assign Notification Service: `4002:4002`
   - Document port usage trong README để team aware

4. **Docker Compose Health Checks**:
   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "pg_isready -U postgres -h localhost"]
     interval: 10s
     timeout: 5s
     retries: 5
   ```

**Lesson Learned**: Systematic port management rất quan trọng trong microservices architecture. Tôi đã tạo port allocation table để team reference.

---

## 4. TỰ ĐÁNH GIÁ MỨC ĐỘ ĐÓNG GÓP: 14%

Tôi tự đánh giá mức độ đóng góp của bản thân trong nhóm là **14%**, dựa trên:

**Công việc đã hoàn thành:**
- ✅ Cấu hình hoàn chỉnh Docker Compose cho Notification Service (infrastructure + service)
- ✅ Build và deploy service nhiều lần trong development cycle
- ✅ Cấu hình API Gateway routing cho notification endpoints
- ✅ Debug và giải quyết technical issues (RabbitMQ, port conflicts)
- ✅ Tích hợp Notification Service với microservices ecosystem

**Scope of Work:**
- Notification Service là 1 trong 7+ microservices của hệ thống
- Docker Compose configuration cho 1 service trong multi-service architecture
- Kong routing cho 1 subset của API endpoints

**Contribution Context:**
- Project có nhiều thành phần phức tạp: security tests, multiple services, infrastructure, CI/CD
- Tôi focus vào 1 service specific area (notification) và infrastructure configuration
- Team size và workload distribution ảnh hưởng đến percentage

Tôi tin rằng 14% là con số công bằng và phản ánh đúng scope công việc tôi đã thực hiện so với tổng thể project.

---

## PHỤ LỤC: TECHNICAL ARTIFACTS

### A. Docker Compose Configuration Examples

**Infrastructure Configuration (excerpt from docker-compose.infra.yml):**
```yaml
notification-service-db:
  image: postgres:16-alpine
  container_name: notification-service-db
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: 123456
    POSTGRES_DB: notificationdb
  ports:
    - "5437:5432"
  volumes:
    - notificationdb_data:/var/lib/postgresql/data
  networks:
    - internal
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U postgres -h localhost"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**Service Configuration (excerpt from docker-compose.services.yml):**
```yaml
notification-service:
  build:
    context: .
    dockerfile: Dockerfile.services.local
    target: notification-service
  container_name: notification-service
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://notification-service-db:5432/notificationdb
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: 123456
    SPRING_RABBITMQ_HOST: rabbitmq
    SPRING_RABBITMQ_PORT: 5672
  depends_on:
    notification-service-db:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  ports:
    - "4002:4002"
  networks:
    - internal
```

### B. Commands Used During Development

```bash
# Build và deploy
mvn clean install -DskipTests
docker compose up -d --build notification-service

# Debug và monitoring
docker compose logs -f notification-service
docker compose ps
docker exec -it notification-service bash

# Troubleshooting
docker compose restart notification-service
docker compose down && docker compose up -d
```

---

**Xác nhận:** Bản tự đánh giá này được viết trung thực, phản ánh đúng công việc tôi đã thực hiện trong dự án FortressBank.

**Chữ ký:** ___________________  
**Ngày:** 05/01/2026

# 🚀 HƯỚNG DẪN DEMO BACKUP SERVICE - FORTRESSBANK

## 📋 MỤC LỤC
1. [Tổng quan](#tổng-quan)
2. [Chuẩn bị môi trường](#chuẩn-bị-môi-trường)
3. [Build và Deploy](#build-và-deploy)
4. [Kịch bản Demo](#kịch-bản-demo)
5. [API Reference](#api-reference)
6. [Troubleshooting](#troubleshooting)

---

## 🎯 TỔNG QUAN

### Backup Service là gì?
**Backup Service** là microservice chuyên dụng cho việc sao lưu và phục hồi dữ liệu trong hệ thống FortressBank. Service này:

✅ **Tự động backup** các database quan trọng theo lịch (mặc định: 2h sáng hàng ngày)
✅ **Manual backup** theo yêu cầu qua REST API
✅ **Restore dữ liệu** từ các bản backup
✅ **Quản lý backup** với metadata tracking, checksum validation
✅ **Cleanup tự động** các backup cũ (retention: 30 ngày)

### Kiến trúc

```
┌─────────────────────────────────────────────────────────────┐
│                    BACKUP SERVICE                           │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │   Scheduler  │  │   REST API   │  │  Restore API    │  │
│  │  (Cron Jobs) │  │  /api/backup │  │  /api/restore   │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬────────┘  │
│         │                  │                    │           │
│         └──────────────────┼────────────────────┘           │
│                            ▼                                │
│                 ┌──────────────────────┐                    │
│                 │   BackupService      │                    │
│                 │  - pg_dump via Docker│                    │
│                 │  - Compression (gzip)│                    │
│                 │  - Checksum (SHA-256)│                    │
│                 └──────────┬───────────┘                    │
│                            │                                │
│                            ▼                                │
│                ┌───────────────────────┐                    │
│                │  backupdb (metadata)  │                    │
│                └───────────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
        ┌──────────────────────────────────────────┐
        │  Target Databases (via docker exec)      │
        ├──────────────────────────────────────────┤
        │  • user-service-db (userdb)              │
        │  • account-service-db (accountdb)        │
        │  • transaction-service-db (transactiondb)│
        │  • audit-service-db (auditdb)            │
        └──────────────────────────────────────────┘
```

---

## 🛠️ CHUẨN BỊ MÔI TRƯỜNG

### Prerequisites
- ✅ Docker Desktop installed
- ✅ Java 21 JDK
- ✅ Maven 3.9+
- ✅ Git
- ✅ Postman (để test API)
- ✅ 8GB RAM khả dụng

### Kiểm tra môi trường

```bash
# Kiểm tra Java
java -version
# Kết quả mong đợi: openjdk version "21.x.x"

# Kiểm tra Maven
mvn -version
# Kết quả mong đợi: Apache Maven 3.9.x

# Kiểm tra Docker
docker --version
docker-compose --version
```

---

## 🏗️ BUILD VÀ DEPLOY

### Bước 1: Clone Repository (nếu chưa có)

```bash
cd "C:\Users\Ngo Minh Tri\OneDrive\Máy tính\uit\bank"
cd fortressbank
```

### Bước 2: Build toàn bộ project

```bash
# Clean và build tất cả modules (bao gồm backup-service)
mvn clean install -DskipTests

# Nếu gặp lỗi, build từng module:
mvn clean install -pl shared-kernel -am
mvn clean install -pl backup-service -am
```

### Bước 3: Start Infrastructure Services

```bash
# Start database và infrastructure
docker-compose -f docker-compose.yml up -d user-service-db account-service-db transaction-service-db audit-service-db backup-service-db redis

# Kiểm tra containers đã chạy
docker ps | grep -E "db|redis"
```

Chờ 10-15 giây để databases khởi động hoàn toàn.

### Bước 4: Start Application Services

```bash
# Start các services (bao gồm backup-service)
docker-compose -f docker-compose.yml up -d

# Theo dõi logs của backup-service
docker logs -f backup-service
```

### Bước 5: Verify Services

```bash
# Kiểm tra health của backup-service
curl http://localhost:4006/actuator/health

# Kết quả mong đợi:
# {"status":"UP"}
```

**Dashboard URL:**
- Eureka: http://localhost:8761
- Backup Service Actuator: http://localhost:4006/actuator/health

---

## 🎬 KỊCH BẢN DEMO

### DEMO 1: Manual Backup - Full System

#### Bước 1: Tạo dữ liệu test (nếu chưa có)

```bash
# Truy cập vào user-service-db và tạo một số users mẫu
docker exec -it user-service-db psql -U postgres -d userdb -c \
"INSERT INTO users (id, username, email, full_name, created_at) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'demo_user', 'demo@fortressbank.com', 'Demo User', NOW());"

# Kiểm tra dữ liệu đã tạo
docker exec -it user-service-db psql -U postgres -d userdb -c "SELECT COUNT(*) FROM users;"
```

#### Bước 2: Thực hiện Full Backup qua API

**Request:**
```bash
curl -X POST http://localhost:4006/api/backup \
  -H "Content-Type: application/json" \
  -d '{
    "backupType": "FULL",
    "backupName": "demo_full_backup",
    "compressed": true,
    "encrypted": false,
    "initiatedBy": "admin_demo"
  }'
```

**Response mẫu:**
```json
{
  "success": true,
  "message": "Backup initiated successfully",
  "data": {
    "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "backupName": "demo_full_backup",
    "backupType": "FULL",
    "status": "COMPLETED",
    "startedAt": "2026-01-04T14:30:00",
    "completedAt": "2026-01-04T14:32:15",
    "totalSizeBytes": 5242880,
    "totalSizeFormatted": "5.00 MB",
    "backupPath": "/app/backups/2026-01-04_14-30-00_a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "compressed": true,
    "encrypted": false,
    "checksum": "8f3b9c1a2d4e5f6789abcdef01234567890abcdef",
    "initiatedBy": "admin_demo",
    "serviceBackups": [
      {
        "serviceName": "user-service",
        "databaseName": "userdb",
        "status": "COMPLETED",
        "fileSizeFormatted": "1.2 MB",
        "recordCount": 15000,
        "backupDurationMs": 2350
      },
      {
        "serviceName": "account-service",
        "databaseName": "accountdb",
        "status": "COMPLETED",
        "fileSizeFormatted": "2.1 MB",
        "recordCount": 25000,
        "backupDurationMs": 3120
      },
      {
        "serviceName": "transaction-service",
        "databaseName": "transactiondb",
        "status": "COMPLETED",
        "fileSizeFormatted": "1.5 MB",
        "recordCount": 18000,
        "backupDurationMs": 2890
      },
      {
        "serviceName": "audit-service",
        "databaseName": "auditdb",
        "status": "COMPLETED",
        "fileSizeFormatted": "200 KB",
        "recordCount": 5000,
        "backupDurationMs": 1100
      }
    ],
    "durationMs": 135000
  }
}
```

#### Bước 3: Kiểm tra backup files

```bash
# Xem các file backup đã tạo
ls -lah ./backups/

# Output mẫu:
# drwxr-xr-x  2026-01-04_14-30-00_a1b2c3d4-e5f6-7890-abcd-ef1234567890/
#   -rw-r--r--  user-service_userdb.sql.gz (1.2 MB)
#   -rw-r--r--  account-service_accountdb.sql.gz (2.1 MB)
#   -rw-r--r--  transaction-service_transactiondb.sql.gz (1.5 MB)
#   -rw-r--r--  audit-service_auditdb.sql.gz (200 KB)
```

#### Bước 4: Xem danh sách tất cả backups

**Request:**
```bash
curl http://localhost:4006/api/backup
```

---

### DEMO 2: Disaster Recovery - Restore Data

#### Kịch bản: Simulate data loss và restore

#### Bước 1: Xóa dữ liệu (SIMULATE DISASTER)

```bash
# ⚠️ CẢNH BÁO: Lệnh này sẽ XÓA DỮ LIỆU! Chỉ dùng cho demo!

# Xóa tất cả users trong userdb
docker exec -it user-service-db psql -U postgres -d userdb -c "DELETE FROM users;"

# Xác nhận dữ liệu đã bị xóa
docker exec -it user-service-db psql -U postgres -d userdb -c "SELECT COUNT(*) FROM users;"
# Kết quả: 0
```

Tại thời điểm này, hệ thống đã **mất dữ liệu**!

#### Bước 2: Restore từ backup

**Request:**
```bash
# Lấy backupId từ response ở Demo 1 (hoặc từ GET /api/backup)
# Ví dụ: backupId = a1b2c3d4-e5f6-7890-abcd-ef1234567890

curl -X POST http://localhost:4006/api/restore \
  -H "Content-Type: application/json" \
  -d '{
    "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "stopServices": true,
    "clearRedisCache": true,
    "verifyIntegrity": true,
    "initiatedBy": "admin_demo"
  }'
```

**Response mẫu:**
```json
{
  "success": true,
  "message": "Restore completed successfully",
  "data": {
    "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "COMPLETED",
    "startedAt": "2026-01-04T14:45:00",
    "completedAt": "2026-01-04T14:47:30",
    "durationMs": 150000,
    "restoredServices": [
      "user-service",
      "account-service",
      "transaction-service",
      "audit-service"
    ],
    "success": true
  }
}
```

#### Bước 3: Verify dữ liệu đã được restore

```bash
# Kiểm tra dữ liệu đã quay lại
docker exec -it user-service-db psql -U postgres -d userdb -c "SELECT COUNT(*) FROM users;"

# Kiểm tra chi tiết user demo
docker exec -it user-service-db psql -U postgres -d userdb -c \
"SELECT username, email, full_name FROM users WHERE username='demo_user';"
```

**✅ Dữ liệu đã được phục hồi thành công!**

---

### DEMO 3: Single Service Backup

#### Use case: Chỉ backup một service cụ thể

**Request:**
```bash
curl -X POST http://localhost:4006/api/backup \
  -H "Content-Type: application/json" \
  -d '{
    "backupType": "SINGLE_SERVICE",
    "backupName": "transaction_service_backup",
    "serviceNames": ["transaction-service"],
    "compressed": true,
    "initiatedBy": "admin_demo"
  }'
```

---

### DEMO 4: Scheduled Automatic Backup

#### Kiểm tra scheduled backup

```bash
# Xem logs của scheduler
docker logs backup-service | grep "scheduled"

# Output mẫu:
# 2026-01-05 02:00:00 - Starting scheduled automatic full backup
# 2026-01-05 02:02:15 - Scheduled backup completed successfully
```

Scheduler sẽ tự động chạy vào **2:00 AM mỗi ngày**.

Để test ngay lập tức, có thể thay đổi cron expression trong `application.yml`:
```yaml
backup:
  schedule-cron: "0 */5 * * * ?" # Chạy mỗi 5 phút
```

---

### DEMO 5: Backup Cleanup

#### Xóa các backup cũ tự động

**Request:**
```bash
curl -X POST http://localhost:4006/api/backup/cleanup
```

**Response:**
```json
{
  "success": true,
  "message": "Old backups cleaned up successfully"
}
```

---

## 📚 API REFERENCE

### 1. Create Backup

**Endpoint:** `POST /api/backup`

**Request Body:**
```json
{
  "backupType": "FULL|SINGLE_SERVICE|CUSTOM",
  "backupName": "optional_name",
  "serviceNames": ["service1", "service2"],  // Required for SINGLE_SERVICE/CUSTOM
  "compressed": true,
  "encrypted": false,
  "initiatedBy": "username"
}
```

**Backup Types:**
- `FULL`: Backup tất cả services (user, account, transaction, audit)
- `SINGLE_SERVICE`: Backup 1 service cụ thể
- `CUSTOM`: Backup các services được chọn

---

### 2. Get All Backups

**Endpoint:** `GET /api/backup`

**Response:**
```json
{
  "success": true,
  "message": "Backups retrieved successfully",
  "data": [
    {
      "backupId": "uuid",
      "backupName": "backup_name",
      "backupType": "FULL",
      "status": "COMPLETED",
      "totalSizeFormatted": "5.2 MB",
      "createdAt": "2026-01-04T14:30:00",
      ...
    }
  ]
}
```

---

### 3. Get Backup by ID

**Endpoint:** `GET /api/backup/{backupId}`

**Response:** Chi tiết 1 backup

---

### 4. Restore Backup

**Endpoint:** `POST /api/restore`

**Request Body:**
```json
{
  "backupId": "uuid",
  "serviceNames": ["user-service"],  // Optional, null = restore all
  "stopServices": true,
  "clearRedisCache": true,
  "verifyIntegrity": true,
  "initiatedBy": "username"
}
```

---

### 5. Cleanup Old Backups

**Endpoint:** `POST /api/backup/cleanup`

Xóa các backup cũ hơn retention period (mặc định 30 ngày).

---

## 🐛 TROUBLESHOOTING

### Issue 1: backup-service không start

**Triệu chứng:**
```
Error: Cannot connect to backup-service-db
```

**Giải pháp:**
```bash
# Kiểm tra DB đã chạy chưa
docker ps | grep backup-service-db

# Nếu chưa chạy, start lại
docker-compose up -d backup-service-db

# Chờ 10s rồi start lại backup-service
docker-compose up -d backup-service
```

---

### Issue 2: pg_dump failed

**Triệu chứng:**
```
ERROR: pg_dump failed with exit code: 1
```

**Giải pháp:**
```bash
# Kiểm tra container database target có chạy không
docker ps | grep user-service-db

# Test kết nối từ backup-service
docker exec backup-service sh -c "PGPASSWORD=123456 pg_dump -h user-service-db -U postgres -d userdb --schema-only"

# Nếu lỗi, restart target database
docker restart user-service-db
```

---

### Issue 3: Restore failed - file not found

**Triệu chứng:**
```
ERROR: Backup file not found
```

**Giải pháp:**
```bash
# Kiểm tra backup files
ls -la ./backups/

# Kiểm tra volume mount
docker inspect backup-service | grep Mounts

# Đảm bảo volume được mount đúng:
# ./backups:/app/backups
```

---

### Issue 4: Permission denied khi backup

**Triệu chứng:**
```
ERROR: Permission denied: /app/backups
```

**Giải pháp:**
```bash
# Cấp quyền cho thư mục backups
chmod -R 777 ./backups

# Hoặc chạy với sudo (Linux/Mac)
sudo docker-compose up -d backup-service
```

---

## 📊 METRICS & MONITORING

### View backup statistics

```bash
# Tổng dung lượng tất cả backups
du -sh ./backups/*

# Số lượng backups
ls -1 ./backups/ | wc -l

# Backup mới nhất
ls -lt ./backups/ | head -n 2
```

### Database queries

```bash
# Số lượng backups thành công
docker exec -it backup-service-db psql -U postgres -d backupdb -c \
"SELECT COUNT(*) FROM backup_metadata WHERE status='COMPLETED';"

# Tổng dung lượng backups
docker exec -it backup-service-db psql -U postgres -d backupdb -c \
"SELECT pg_size_pretty(SUM(total_size_bytes)::bigint) FROM backup_metadata WHERE status='COMPLETED';"

# Backups trong 7 ngày qua
docker exec -it backup-service-db psql -U postgres -d backupdb -c \
"SELECT backup_name, backup_type, status, created_at FROM backup_metadata WHERE created_at > NOW() - INTERVAL '7 days' ORDER BY created_at DESC;"
```

---

## 🎓 ĐIỂM DEMO CHO BÁO CÁO ĐỒ ÁN

### Các điểm nổi bật cần nhấn mạnh:

1. **Tính năng Backup tự động**
   - Scheduled backup hàng ngày (production-ready)
   - Retention policy tự động cleanup

2. **Disaster Recovery**
   - Demo khả năng phục hồi dữ liệu sau sự cố
   - Restore theo đúng thứ tự dependency (user → account → transaction)

3. **Metadata Tracking**
   - Checksum validation đảm bảo integrity
   - Detailed backup history
   - Service-level granularity

4. **Microservices Architecture**
   - Tích hợp với Eureka Discovery
   - Follows Spring Boot conventions
   - Docker orchestration

5. **Production-Ready Features**
   - Compression (tiết kiệm storage)
   - Encryption support (security)
   - Health checks
   - Structured logging

---

## 📝 CHECKLIST TRƯỚC KHI DEMO

- [ ] Tất cả containers đang chạy (`docker ps`)
- [ ] backup-service health check OK
- [ ] Có dữ liệu test trong databases
- [ ] Thư mục `./backups` đã được tạo
- [ ] Postman collection imported
- [ ] Đã test ít nhất 1 lần backup thành công
- [ ] Đã test restore thành công
- [ ] Chuẩn bị slides PowerPoint với screenshots

---

## 🎯 KẾT LUẬN

Backup Service là một tính năng **quan trọng** và **thiết yếu** đối với ứng dụng ngân hàng điện tử. Với kiến trúc microservices hiện đại và các tính năng production-ready, service này đảm bảo:

✅ **Data Safety**: Dữ liệu quan trọng được backup định kỳ
✅ **Quick Recovery**: Phục hồi nhanh chóng khi có sự cố
✅ **Compliance**: Đáp ứng yêu cầu audit và compliance
✅ **Scalability**: Dễ dàng mở rộng cho nhiều services hơn

---

**Good luck với bài demo! 🚀**
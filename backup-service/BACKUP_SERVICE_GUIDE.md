# 💾 Backup Service - FortressBank

Dịch vụ Backup Service là thành phần quan trọng của FortressBank, chịu trách nhiệm backup tự động và phục hồi dữ liệu ngân hàng.

## 🎯 Tính Năng Chính

| Tính Năng | Mô Tả |
|-----------|-------|
| **Backup Tự Động** | Chạy lúc 2 AM hàng ngày (có thể cấu hình) |
| **Backup Thủ Công** | API REST để backup bất kỳ lúc nào |
| **Nhiều Loại Backup** | FULL (tất cả), SINGLE_SERVICE (một dịch vụ), CUSTOM (tùy chọn) |
| **Nén Dữ Liệu** | gzip compression tự động |
| **Kiểm Tra Tính Toàn Vẹn** | SHA-256 checksum |
| **Phục Hồi Dữ Liệu** | Khôi phục từ bất kỳ backup nào |
| **Xóa Tự Động** | Xóa backup cũ (mặc định 30 ngày) |
| **Lưu Lịch Sử** | Theo dõi chi tiết tất cả backup |

## 🏗️ Kiến Trúc

```
Backup Service
    ↓
┌─────────────────────────────────┐
│ Scheduler (2 AM hàng ngày)     │ ← Tự động
│ REST API (/api/backup)         │ ← Thủ công
└──────────┬──────────────────────┘
           ↓
┌─────────────────────────────────┐
│ PostgreSQL (backupdb)           │
│ - backup_metadata               │
│ - service_backup_info           │
│ - outbox_events                 │
└──────────┬──────────────────────┘
           ↓
    Databases: userdb, accountdb,
    transactiondb, auditdb
```

## 🚀 Cài Đặt & Chạy

### Yêu Cầu
- Docker & Docker Compose
- Java 21
- Maven 3.9+

### Build & Deploy

```bash
# Build locally
mvn clean install -pl backup-service -am

# Chạy với Docker Compose
docker-compose up -d backup-service

# Kiểm tra sức khỏe
curl http://localhost:4006/actuator/health
```

### Cấu Hình (application.yml)

```yaml
backup:
  backup-directory: /app/backups          # Thư mục lưu backup
  retention-days: 30                      # Giữ backup bao nhiêu ngày
  compression-enabled: true               # Bật nén dữ liệu
  encryption-enabled: false               # Mã hóa (chưa hỗ trợ)
  schedule-cron: "0 0 2 * * *"           # Cron: 2 AM hàng ngày (6 field)

server:
  port: 4006
```

## 📡 API Endpoints

### 1️⃣ Tạo Backup (POST /api/backup)

**Request:**
```json
{
  "backupType": "FULL",
  "backupName": "full_backup_jan4",
  "compressed": true,
  "encrypted": false,
  "initiatedBy": "admin"
}
```

**Loại Backup:**
- `FULL` - Backup tất cả dịch vụ
- `SINGLE_SERVICE` - Backup một dịch vụ
- `CUSTOM` - Backup dịch vụ được chọn

**Response:**
```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "backupId": "uuid-xxx",
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-04T14:45:00Z"
  }
}
```

### 2️⃣ Xem Tất Cả Backup (GET /api/backup)

```bash
curl http://localhost:4006/api/backup
```

**Response:** Danh sách tất cả backup

### 3️⃣ Xem Chi Tiết Backup (GET /api/backup/{backupId})

```bash
curl http://localhost:4006/api/backup/550e8400-e29b-41d4-a716-446655440000
```

### 4️⃣ Phục Hồi Backup (POST /api/restore)

**Request:**
```json
{
  "backupId": "uuid-xxx",
  "stopServices": true,        # Dừng dịch vụ trước khi phục hồi
  "clearRedisCache": true,     # Xóa Redis cache
  "verifyIntegrity": true      # Kiểm tra tính toàn vẹn
}
```

### 5️⃣ Xóa Backup Cũ (POST /api/backup/cleanup)

```bash
curl -X POST http://localhost:4006/api/backup/cleanup
```

## 🗄️ Cơ Sở Dữ Liệu

### Bảng: backup_metadata
Lưu thông tin tổng quát về backup

| Cột | Kiểu | Mô Tả |
|-----|------|-------|
| backup_id | UUID | Khóa chính |
| backup_name | VARCHAR | Tên backup |
| backup_type | VARCHAR | FULL/SINGLE_SERVICE/CUSTOM |
| status | VARCHAR | PENDING/IN_PROGRESS/COMPLETED/FAILED |
| total_size_bytes | BIGINT | Kích thước backup |
| backup_path | VARCHAR | Đường dẫn file |
| compressed | BOOLEAN | Có nén hay không |
| checksum | VARCHAR | SHA-256 hash |
| initiated_by | VARCHAR | Người tạo |
| created_at | TIMESTAMP | Thời gian tạo |

### Bảng: service_backup_info
Lưu thông tin chi tiết từng dịch vụ trong backup

| Cột | Kiểu | Mô Tả |
|-----|------|-------|
| id | UUID | Khóa chính |
| backup_id | UUID | Liên kết đến backup_metadata |
| service_name | VARCHAR | Tên dịch vụ |
| database_name | VARCHAR | Tên database |
| container_name | VARCHAR | Tên container Docker |
| file_path | VARCHAR | Đường dẫn file backup |
| file_size_bytes | BIGINT | Kích thước file |
| record_count | BIGINT | Số record |
| checksum | VARCHAR | Hash file |
| status | VARCHAR | Trạng thái |

### Bảng: outbox_events
Lưu sự kiện outbox (Event Sourcing)

| Cột | Kiểu | Mô Tả |
|-----|------|-------|
| event_id | UUID | Khóa chính |
| aggregate_type | VARCHAR | Loại aggregate |
| aggregate_id | VARCHAR | ID của aggregate |
| event_type | VARCHAR | Loại sự kiện |
| exchange | VARCHAR | RabbitMQ exchange |
| routing_key | VARCHAR | RabbitMQ routing key |
| payload | TEXT | Dữ liệu sự kiện (JSON) |
| status | VARCHAR | PENDING/PROCESSED |
| created_at | TIMESTAMP | Thời gian tạo |

## 🔄 Quy Trình Backup & Restore

### Quy Trình Backup
1. Tạo bản ghi metadata trong DB
2. Chạy `pg_dump` cho từng database
3. Nén file (nếu bật)
4. Tính toán checksum SHA-256
5. Lưu thông tin vào metadata
6. Xóa backup cũ hơn 30 ngày

### Quy Trình Restore
1. Kiểm tra tính toàn vẹn backup
2. Dừng tất cả dịch vụ (nếu yêu cầu)
3. Giải nén file (nếu cần)
4. Đẩy file vào container Docker
5. Chạy `psql` để restore
6. Xóa Redis cache (nếu yêu cầu)
7. Khởi động lại dịch vụ

## 📊 Monitoring & Debug

### Kiểm Tra Sức Khỏe

```bash
curl http://localhost:4006/actuator/health
```

### Xem Logs

```bash
docker logs backup-service -f
```

### Truy Vấn SQL

```sql
-- Tổng số backup
SELECT COUNT(*) FROM backup_metadata WHERE status='COMPLETED';

-- Tổng dung lượng backup
SELECT pg_size_pretty(SUM(total_size_bytes)::bigint)
FROM backup_metadata WHERE status='COMPLETED';

-- 10 backup gần nhất
SELECT backup_name, status, total_size_bytes, created_at
FROM backup_metadata
ORDER BY created_at DESC LIMIT 10;

-- Xem dung lượng từng dịch vụ
SELECT service_name, SUM(file_size_bytes) as total_size
FROM service_backup_info
GROUP BY service_name;
```

## 🔐 Bảo Mật

✅ **Đã triển khai:**
- Backup lưu với quyền hạn chế
- Checksum kiểm tra tính toàn vẹn
- Ghi log tất cả thao tác
- Spring Security OAuth2

⚠️ **Nên làm:**
- Production: Bật mã hóa AES-256
- Backup offsite (cloud storage)
- Định kỳ test restore
- Giám sát backup thất bại

## ❌ Sự Cố & Giải Pháp

| Lỗi | Nguyên Nhân | Giải Pháp |
|-----|-------------|----------|
| `pg_dump failed` | Database container không chạy | `docker restart {db-container}` |
| `Permission denied /app/backups` | Quyền hạn không đủ | `chmod -R 777 ./backups` |
| `Restore failed` | File backup hỏng | Kiểm tra checksum, backup lại |
| `Cron không chạy` | Cron expression sai | 6 field: `second minute hour day month weekday` |
| `Service startup lâu` | Config server chậm | Chờ config-server healthy trước |

## 🛠️ Công Nghệ Sử Dụng

| Thành Phần | Phiên Bản | Mục Đích |
|-----------|----------|---------|
| Spring Boot | 3.5.6 | Framework chính |
| PostgreSQL | 16 | Lưu metadata |
| Docker SDK | - | Tương tác container |
| Flyway | - | Database migration |
| Lombok | - | Giảm boilerplate code |
| Spring Scheduler | - | Cron jobs |
| JPA/Hibernate | - | ORM |

## 📋 Checklist Deployment

- [ ] Kiểm tra Java 21 cài đặt
- [ ] Build Maven success
- [ ] Docker Compose chạy tất cả services
- [ ] Kiểm tra backup-service health endpoint
- [ ] Test POST /api/backup
- [ ] Test GET /api/backup
- [ ] Test POST /api/restore với backup cũ
- [ ] Xác nhận log không có lỗi
- [ ] Kiểm tra cron job vào 2 AM

## 📚 Tài Liệu Thêm

- **BACKUP_DEMO_GUIDE.md** - Hướng dẫn demo chi tiết
- **docker-compose.yml** - Cấu hình Docker
- **Postman Collection** - Backup-Service-API.postman_collection.json

## ❓ FAQ

**Q: Backup store ở đâu?**
A: Mặc định `/app/backups` trong container, mount sang `./backups` trên host.

**Q: Có thể backup lên cloud không?**
A: Chưa hỗ trợ sẵn, có thể tích hợp MinIO hoặc S3.

**Q: Backup mất bao lâu?**
A: Tùy dung lượng, thường 5-15 phút cho full backup.

**Q: Có thể restore riêng một database không?**
A: Có, dùng `backupType: SINGLE_SERVICE` với service_name.

**Q: Backup có encrypt không?**
A: Chưa, hãy mã hóa từ ngoài hoặc enable AES-256 config.

---

**Version:** 1.0  
**Last Updated:** 2026-01-04  
**Status:** ✅ Production Ready

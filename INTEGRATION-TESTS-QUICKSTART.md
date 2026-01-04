# 🎯 Integration Tests - Quick Start Guide

## ⚡ Chạy tests nhanh

### Windows:
```batch
run-integration-tests.bat
```

### Linux/Mac:
```bash
chmod +x run-integration-tests.sh
./run-integration-tests.sh
```

### Maven trực tiếp:
```bash
# Tất cả services
mvn test -pl user-service,account-service,transaction-service

# Chỉ một service
mvn test -pl user-service
```

---

## 📋 Checklist trước khi chạy

- [ ] Docker Desktop đang chạy
- [ ] Java 21 đã cài đặt
- [ ] Maven 3.8+ đã cài đặt
- [ ] Ít nhất 4GB RAM cho Docker

---

## 📊 Test Coverage

| Service | Test Classes | Test Cases | Coverage |
|---------|-------------|------------|----------|
| user-service | 2 | 8 | Controllers + Services |
| account-service | 3 | 14 | Controllers + Services + Repositories |
| transaction-service | 4 | 27 | Full stack + Concurrency |
| **Total** | **9** | **49** | **Comprehensive** |

---

## 🐳 Containers sử dụng

- **PostgreSQL 15** - Real database (thay H2)
- **Redis 7** - Cache & sessions
- **RabbitMQ 3.12** - Message broker

---

## 📁 Structure

```
service/
├── src/
│   └── test/
│       ├── java/
│       │   └── com/uit/{service}/
│       │       ├── BaseIntegrationTest.java      ← Testcontainers setup
│       │       ├── controller/
│       │       │   └── *IntegrationTest.java     ← API tests
│       │       ├── service/
│       │       │   └── *IntegrationTest.java     ← Business logic tests
│       │       ├── repository/
│       │       │   └── *IntegrationTest.java     ← Database tests
│       │       └── helper/
│       │           └── TestDataBuilder.java      ← Test utilities
│       └── resources/
│           └── application-test.yml              ← Test config
```

---

## 🎓 Test Types đã triển khai

### 1. Controller Tests (API Layer)
- REST endpoints
- Request/Response validation
- Security & Authorization
- Error handling

### 2. Service Tests (Business Logic)
- Complex business rules
- Transaction management
- Data transformations
- Integration với external services

### 3. Repository Tests (Database)
- Complex queries
- Pagination & Sorting
- Database indexes
- Batch operations

---

## 💡 Tips

### Chạy test cụ thể:
```bash
mvn test -pl transaction-service -Dtest=TransactionRepositoryIntegrationTest
```

### Debug mode:
```bash
mvn test -pl user-service -X
```

### Skip tests khi build:
```bash
mvn clean install -DskipTests
```

### Xem logs chi tiết:
```bash
mvn test -pl account-service -Dlogging.level.com.uit.accountservice=DEBUG
```

---

## 🚨 Common Issues

### "Docker not found"
→ Start Docker Desktop

### "Port already in use"
→ Stop conflicting services (PostgreSQL, Redis)

### "Tests timeout"
→ Increase Docker memory (Settings > Resources)

### "Container failed to start"
→ Check Docker logs: `docker logs <container-id>`

---

## 📚 Documentation

- **Comprehensive Guide**: `INTEGRATION-TESTS-README.md`
- **Implementation Summary**: `INTEGRATION-TESTS-SUMMARY.md`
- **This Quick Start**: `INTEGRATION-TESTS-QUICKSTART.md`

---

## ✨ Highlights

✅ **49 integration tests** covering 3 microservices
✅ **Real infrastructure** (PostgreSQL, Redis, RabbitMQ)
✅ **Production-ready** code quality
✅ **Fast execution** (~1 minute with container reuse)
✅ **CI/CD compatible**

---

## 📞 Support

Nếu gặp vấn đề:
1. Đọc `INTEGRATION-TESTS-README.md`
2. Check Docker logs
3. Run with `-X` flag để xem debug logs
4. Create issue với logs chi tiết

---

**Happy Testing! 🚀**

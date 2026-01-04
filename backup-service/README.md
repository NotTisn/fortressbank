# 💾 Backup Service - FortressBank

## 📖 Overview

The **Backup Service** is a critical microservice in the FortressBank ecosystem responsible for automated backup and disaster recovery of all critical banking data. It ensures data safety, compliance, and business continuity.

## 🎯 Key Features

- ✅ **Automated Scheduled Backups** - Daily backups at 2 AM (configurable cron)
- ✅ **Manual On-Demand Backups** - REST API for immediate backups
- ✅ **Granular Backup Types** - Full, Single Service, or Custom service selection
- ✅ **Data Compression** - gzip compression to save storage
- ✅ **Integrity Validation** - SHA-256 checksums for all backups
- ✅ **Disaster Recovery** - Restore from any backup with dependency management
- ✅ **Retention Policy** - Auto-cleanup of old backups (30 days default)
- ✅ **Metadata Tracking** - Detailed backup history and statistics
- ✅ **Production Ready** - Health checks, logging, metrics

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   Backup Service                         │
│                                                          │
│  ┌─────────────┐     ┌──────────────┐                  │
│  │  Scheduler  │────▶│ BackupService │                  │
│  │ (Cron Jobs) │     └───────┬──────┘                  │
│  └─────────────┘             │                          │
│                               │                          │
│  ┌─────────────┐             │                          │
│  │ REST API    │────────────┘                          │
│  │ /api/backup │                                        │
│  └─────────────┘                                        │
│                                                          │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────────────────────────┐               │
│  │  PostgreSQL (backupdb)              │               │
│  │  - backup_metadata                  │               │
│  │  - service_backup_info              │               │
│  └─────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
                 │
                 ▼
    ┌────────────────────────────┐
    │   Target Databases         │
    │   (via docker exec)        │
    ├────────────────────────────┤
    │  • userdb                  │
    │  • accountdb               │
    │  • transactiondb           │
    │  • auditdb                 │
    └────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21
- Maven 3.9+

### Build & Run

```bash
# Build
mvn clean install -pl backup-service -am

# Run with Docker Compose
docker-compose up -d backup-service

# Check health
curl http://localhost:4006/actuator/health
```

### Configuration

Key configuration in `application.yml`:

```yaml
backup:
  backup-directory: /app/backups
  retention-days: 30
  compression-enabled: true
  encryption-enabled: false
  schedule-cron: "0 2 * * *"  # 2 AM daily
```

## 📚 API Documentation

### Create Backup

```bash
POST /api/backup
Content-Type: application/json

{
  "backupType": "FULL",
  "backupName": "my_backup",
  "compressed": true,
  "initiatedBy": "admin"
}
```

**Backup Types:**
- `FULL` - All critical services
- `SINGLE_SERVICE` - One specific service
- `CUSTOM` - Selected services

### Get All Backups

```bash
GET /api/backup
```

### Get Backup by ID

```bash
GET /api/backup/{backupId}
```

### Restore Backup

```bash
POST /api/restore
Content-Type: application/json

{
  "backupId": "uuid-here",
  "stopServices": true,
  "clearRedisCache": true,
  "verifyIntegrity": true
}
```

### Cleanup Old Backups

```bash
POST /api/backup/cleanup
```

## 🗄️ Database Schema

### backup_metadata

| Column | Type | Description |
|--------|------|-------------|
| backup_id | UUID | Primary key |
| backup_name | VARCHAR(255) | User-friendly name |
| backup_type | VARCHAR(50) | FULL/SINGLE_SERVICE/CUSTOM |
| status | VARCHAR(50) | PENDING/IN_PROGRESS/COMPLETED/FAILED |
| started_at | TIMESTAMP | Start time |
| completed_at | TIMESTAMP | Completion time |
| total_size_bytes | BIGINT | Total backup size |
| backup_path | VARCHAR(500) | File system path |
| compressed | BOOLEAN | Compression flag |
| checksum | VARCHAR(255) | SHA-256 checksum |
| initiated_by | VARCHAR(100) | User who initiated |

### service_backup_info

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| backup_id | UUID | Foreign key to backup_metadata |
| service_name | VARCHAR(100) | Service name |
| database_name | VARCHAR(100) | Database name |
| container_name | VARCHAR(100) | Docker container name |
| file_path | VARCHAR(500) | Backup file path |
| file_size_bytes | BIGINT | File size |
| record_count | BIGINT | Estimated records |
| checksum | VARCHAR(255) | File checksum |
| status | VARCHAR(50) | Backup status |

## 🔧 Technical Implementation

### Backup Process

1. **Initialize** - Create backup metadata record
2. **Execute pg_dump** - For each database via `docker exec`
3. **Compress** - gzip compression (optional)
4. **Calculate Checksum** - SHA-256 hash
5. **Store Metadata** - Update backup record
6. **Cleanup** - Remove old backups if retention exceeded

### Restore Process

1. **Validate** - Check backup integrity
2. **Order Dependencies** - user → account → transaction → audit
3. **Decompress** - If backup is gzipped
4. **Copy to Container** - Via `docker cp`
5. **Execute psql** - Restore database
6. **Clear Cache** - Redis flush (optional)
7. **Verify** - Check data integrity

### Technologies Used

- **Spring Boot 3.5.6** - Application framework
- **Spring Data JPA** - Database access
- **PostgreSQL 16** - Metadata storage
- **Flyway** - Database migrations
- **Docker SDK** - Container interaction
- **Scheduler** - Spring @Scheduled for cron jobs
- **Lombok** - Boilerplate reduction

## 📊 Monitoring

### Health Check

```bash
curl http://localhost:4006/actuator/health
```

### Metrics

```bash
curl http://localhost:4006/actuator/metrics
```

### Database Queries

```sql
-- Total backups
SELECT COUNT(*) FROM backup_metadata WHERE status='COMPLETED';

-- Total backup size
SELECT pg_size_pretty(SUM(total_size_bytes)::bigint)
FROM backup_metadata WHERE status='COMPLETED';

-- Recent backups
SELECT backup_name, status, created_at
FROM backup_metadata
ORDER BY created_at DESC LIMIT 10;
```

## 🔐 Security Considerations

- ✅ Backups stored with restricted permissions
- ✅ Encryption support (AES-256) - configurable
- ✅ Checksum validation for integrity
- ✅ Audit logging for all operations
- ⚠️ Production: Enable OAuth2 authentication
- ⚠️ Production: Use encrypted storage

## 🐛 Troubleshooting

### Issue: pg_dump failed

**Cause:** Database container not accessible

**Solution:**
```bash
docker ps | grep db
docker restart user-service-db
```

### Issue: Permission denied on /app/backups

**Cause:** Volume mount permissions

**Solution:**
```bash
chmod -R 777 ./backups
```

### Issue: Restore failed

**Cause:** Backup file corruption or missing

**Solution:**
```bash
# Verify backup integrity
curl http://localhost:4006/api/backup/{backupId}

# Check files exist
ls -la ./backups/
```

## 📝 Best Practices

1. **Regular Testing** - Test restore process monthly
2. **Offsite Storage** - Copy backups to cloud storage
3. **Monitoring** - Set up alerts for backup failures
4. **Encryption** - Enable for production environments
5. **Retention** - Adjust based on compliance requirements

## 🤝 Contributing

1. Follow Spring Boot conventions
2. Update Flyway migrations for schema changes
3. Add unit tests for new features
4. Update API documentation

## 📄 License

Copyright © 2026 FortressBank. All rights reserved.

## 👥 Team

Developed by UIT Team for Banking Application Project

## 📞 Support

For issues or questions:
- Check [BACKUP_DEMO_GUIDE.md](../BACKUP_DEMO_GUIDE.md)
- Review logs: `docker logs backup-service`
- Contact: dev-team@fortressbank.com

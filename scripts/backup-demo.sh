#!/bin/bash

# FortressBank Backup Service Demo Script
# This script automates the backup and restore demo

set -e

echo "======================================"
echo "  FortressBank Backup Service Demo   "
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Check if services are running
check_services() {
    print_info "Checking if services are running..."

    if ! docker ps | grep -q "backup-service"; then
        print_error "backup-service is not running!"
        echo "Please start services with: docker-compose up -d"
        exit 1
    fi

    if ! docker ps | grep -q "user-service-db"; then
        print_error "user-service-db is not running!"
        echo "Please start services with: docker-compose up -d"
        exit 1
    fi

    print_success "All required services are running"
}

# Wait for service to be healthy
wait_for_service() {
    print_info "Waiting for backup-service to be healthy..."

    for i in {1..30}; do
        if curl -s http://localhost:4006/actuator/health | grep -q "UP"; then
            print_success "backup-service is healthy"
            return 0
        fi
        echo -n "."
        sleep 2
    done

    print_error "backup-service did not become healthy in time"
    exit 1
}

# Create test data
create_test_data() {
    print_info "Creating test data in user-service-db..."

    docker exec -i user-service-db psql -U postgres -d userdb <<EOF
INSERT INTO users (id, username, email, full_name, created_at)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'demo_user', 'demo@fortressbank.com', 'Demo User', NOW())
ON CONFLICT (id) DO NOTHING;
EOF

    COUNT=$(docker exec -i user-service-db psql -U postgres -d userdb -t -c "SELECT COUNT(*) FROM users;")
    print_success "Test data created. Total users: $COUNT"
}

# Create backup
create_backup() {
    print_info "Creating full backup..."

    RESPONSE=$(curl -s -X POST http://localhost:4006/api/backup \
        -H "Content-Type: application/json" \
        -d '{
            "backupType": "FULL",
            "backupName": "demo_backup",
            "compressed": true,
            "encrypted": false,
            "initiatedBy": "demo_script"
        }')

    BACKUP_ID=$(echo $RESPONSE | grep -o '"backupId":"[^"]*' | cut -d'"' -f4)

    if [ -z "$BACKUP_ID" ]; then
        print_error "Failed to create backup"
        echo "Response: $RESPONSE"
        exit 1
    fi

    print_success "Backup created with ID: $BACKUP_ID"
    echo $BACKUP_ID > /tmp/fortressbank_backup_id.txt

    # Wait for backup to complete
    sleep 5
}

# Simulate disaster
simulate_disaster() {
    print_info "Simulating data loss (deleting users)..."

    docker exec -i user-service-db psql -U postgres -d userdb -c "DELETE FROM users;"

    COUNT=$(docker exec -i user-service-db psql -U postgres -d userdb -t -c "SELECT COUNT(*) FROM users;")
    print_error "Data deleted! Users count: $COUNT"
}

# Restore backup
restore_backup() {
    if [ ! -f /tmp/fortressbank_backup_id.txt ]; then
        print_error "No backup ID found. Please create a backup first."
        exit 1
    fi

    BACKUP_ID=$(cat /tmp/fortressbank_backup_id.txt)
    print_info "Restoring backup: $BACKUP_ID..."

    RESPONSE=$(curl -s -X POST http://localhost:4006/api/restore \
        -H "Content-Type: application/json" \
        -d "{
            \"backupId\": \"$BACKUP_ID\",
            \"stopServices\": true,
            \"clearRedisCache\": true,
            \"verifyIntegrity\": true,
            \"initiatedBy\": \"demo_script\"
        }")

    SUCCESS=$(echo $RESPONSE | grep -o '"success":[^,]*' | cut -d':' -f2)

    if [ "$SUCCESS" == "true" ]; then
        print_success "Restore completed successfully!"
    else
        print_error "Restore failed"
        echo "Response: $RESPONSE"
        exit 1
    fi

    # Wait for restore to complete
    sleep 5
}

# Verify restore
verify_restore() {
    print_info "Verifying restored data..."

    COUNT=$(docker exec -i user-service-db psql -U postgres -d userdb -t -c "SELECT COUNT(*) FROM users;")

    if [ "$COUNT" -gt 0 ]; then
        print_success "Data restored successfully! Users count: $COUNT"

        docker exec -i user-service-db psql -U postgres -d userdb -c \
            "SELECT username, email, full_name FROM users WHERE username='demo_user';"
    else
        print_error "Data not restored properly"
        exit 1
    fi
}

# List backups
list_backups() {
    print_info "Listing all backups..."

    curl -s http://localhost:4006/api/backup | jq '.'
}

# Main menu
show_menu() {
    echo ""
    echo "Select demo scenario:"
    echo "1) Full Demo (Create data → Backup → Delete → Restore)"
    echo "2) Create test data"
    echo "3) Create backup only"
    echo "4) Simulate disaster (delete data)"
    echo "5) Restore from backup"
    echo "6) List all backups"
    echo "7) Verify service health"
    echo "0) Exit"
    echo ""
    read -p "Enter choice: " choice

    case $choice in
        1)
            check_services
            wait_for_service
            create_test_data
            create_backup
            simulate_disaster
            restore_backup
            verify_restore
            print_success "Full demo completed!"
            ;;
        2)
            create_test_data
            ;;
        3)
            create_backup
            ;;
        4)
            simulate_disaster
            ;;
        5)
            restore_backup
            verify_restore
            ;;
        6)
            list_backups
            ;;
        7)
            check_services
            wait_for_service
            ;;
        0)
            echo "Goodbye!"
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            ;;
    esac

    show_menu
}

# Run
show_menu
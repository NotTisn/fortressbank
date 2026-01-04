#!/bin/bash

# Script to run all integration tests for FortressBank microservices
# Author: FortressBank Team
# Date: January 2026

set -e  # Exit on error

echo "======================================"
echo "🚀 FortressBank Integration Tests"
echo "======================================"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running!"
    echo "Please start Docker Desktop and try again."
    exit 1
fi

echo "✅ Docker is running"
echo ""

# Function to run tests for a service
run_service_tests() {
    local service=$1
    echo "======================================"
    echo "📦 Testing: $service"
    echo "======================================"
    
    if mvn test -pl $service -q; then
        echo "✅ $service tests PASSED"
    else
        echo "❌ $service tests FAILED"
        return 1
    fi
    echo ""
}

# Parse command line arguments
if [ $# -eq 0 ]; then
    # Run all services
    echo "Running tests for all services..."
    echo ""
    
    run_service_tests "user-service"
    run_service_tests "account-service"
    run_service_tests "transaction-service"
    
    echo "======================================"
    echo "✅ All integration tests completed!"
    echo "======================================"
else
    # Run specific service
    case $1 in
        user|user-service)
            run_service_tests "user-service"
            ;;
        account|account-service)
            run_service_tests "account-service"
            ;;
        transaction|transaction-service)
            run_service_tests "transaction-service"
            ;;
        *)
            echo "❌ Unknown service: $1"
            echo "Usage: $0 [user|account|transaction]"
            echo "   or: $0  (to run all services)"
            exit 1
            ;;
    esac
fi

echo ""
echo "💡 Tip: Use '$0 [service-name]' to run tests for a specific service"
echo "   Example: $0 user"

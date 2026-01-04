@echo off
REM Script to run all integration tests for FortressBank microservices
REM Author: FortressBank Team
REM Date: January 2026

setlocal enabledelayedexpansion

echo ======================================
echo 🚀 FortressBank Integration Tests
echo ======================================
echo.

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ Error: Docker is not running!
    echo Please start Docker Desktop and try again.
    exit /b 1
)

echo ✅ Docker is running
echo.

if "%1"=="" (
    REM Run all services
    echo Running tests for all services...
    echo.
    
    call :run_service_tests user-service
    if errorlevel 1 exit /b 1
    
    call :run_service_tests account-service
    if errorlevel 1 exit /b 1
    
    call :run_service_tests transaction-service
    if errorlevel 1 exit /b 1
    
    echo ======================================
    echo ✅ All integration tests completed!
    echo ======================================
) else (
    REM Run specific service
    if /i "%1"=="user" (
        call :run_service_tests user-service
    ) else if /i "%1"=="user-service" (
        call :run_service_tests user-service
    ) else if /i "%1"=="account" (
        call :run_service_tests account-service
    ) else if /i "%1"=="account-service" (
        call :run_service_tests account-service
    ) else if /i "%1"=="transaction" (
        call :run_service_tests transaction-service
    ) else if /i "%1"=="transaction-service" (
        call :run_service_tests transaction-service
    ) else (
        echo ❌ Unknown service: %1
        echo Usage: %0 [user^|account^|transaction]
        echo    or: %0  (to run all services^)
        exit /b 1
    )
)

echo.
echo 💡 Tip: Use '%0 [service-name]' to run tests for a specific service
echo    Example: %0 user

exit /b 0

:run_service_tests
    set service=%1
    echo ======================================
    echo 📦 Testing: %service%
    echo ======================================
    
    call mvn test -pl %service% -q
    if errorlevel 1 (
        echo ❌ %service% tests FAILED
        exit /b 1
    )
    
    echo ✅ %service% tests PASSED
    echo.
    exit /b 0

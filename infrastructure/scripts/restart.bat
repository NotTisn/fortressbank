@echo off
setlocal
REM ============================================================================
REM FortressBank - Restart Single Service
REM ============================================================================
REM Usage: restart.bat <service>
REM 
REM Services: config-server, discovery, user-service, account-service,
REM           notification-service, transaction-service, audit-service, risk-engine
REM ============================================================================

if "%~1"=="" (
    echo.
    echo   Usage: restart.bat ^<service^>
    echo.
    echo   Available services:
    echo     config-server        (port 8889)
    echo     discovery            (port 8761)
    echo     user-service         (port 4000)
    echo     account-service      (port 4001)
    echo     notification-service (port 4002)
    echo     transaction-service  (port 4004)
    echo     audit-service        (port 4005)
    echo     risk-engine          (port 4006)
    echo.
    exit /b 1
)

set "SERVICE=%~1"
set "SCRIPT_DIR=%~dp0"
set "INFRA_DIR=%SCRIPT_DIR%.."
set "ROOT=%INFRA_DIR%\.."

REM Map service to dir and port
REM Per copilot-instructions.md §9 ARCHITECTURE
if /i "%SERVICE%"=="config-server"        set "svc_dir=config-server"        & set "svc_port=8889"
if /i "%SERVICE%"=="discovery"            set "svc_dir=discovery"            & set "svc_port=8761"
if /i "%SERVICE%"=="user-service"         set "svc_dir=user-service"         & set "svc_port=4000"
if /i "%SERVICE%"=="account-service"      set "svc_dir=account-service"      & set "svc_port=4001"
if /i "%SERVICE%"=="notification-service" set "svc_dir=notification-service" & set "svc_port=4002"
if /i "%SERVICE%"=="transaction-service"  set "svc_dir=transaction-service"  & set "svc_port=4004"
if /i "%SERVICE%"=="audit-service"        set "svc_dir=audit-service"        & set "svc_port=4005"
if /i "%SERVICE%"=="risk-engine"          set "svc_dir=risk-engine"          & set "svc_port=4006"

if not defined svc_dir (
    echo   Unknown service: %SERVICE%
    exit /b 1
)

echo   Stopping %SERVICE% on port %svc_port%...

REM Kill by port
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%svc_port%" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

timeout /t 2 /nobreak >nul

echo   Starting %SERVICE%...

REM Start in new window
cd /d "%ROOT%\%svc_dir%"
start "%SERVICE%" cmd /k "mvnw.cmd spring-boot:run"

echo   Done. Check the new window for %SERVICE%.

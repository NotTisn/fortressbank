@echo off
setlocal
REM ============================================================================
REM FortressBank - Stop All Services by Known Ports
REM ============================================================================
REM Usage: stop_all.bat [--docker]
REM   --docker    Also stop Docker infrastructure
REM ============================================================================

echo.
echo   Stopping FortressBank Services...
echo.

set "STOP_DOCKER=0"
if /i "%~1"=="--docker" set "STOP_DOCKER=1"

REM Kill Java services by known ports
REM Per copilot-instructions.md §9 ARCHITECTURE
echo   Checking ports: 8889, 8761, 4000, 4001, 4002, 4004, 4005, 4006
for %%p in (8889 8761 4000 4001 4002 4004 4005 4006) do (
    for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%%p" ^| findstr "LISTENING"') do (
        echo     Killing process on port %%p (PID: %%a)
        taskkill /F /PID %%a >nul 2>&1
    )
)

if "%STOP_DOCKER%"=="1" (
    echo.
    echo   Stopping Docker infrastructure...
    cd /d "%~dp0"
    docker compose -f compose-infra-only.yaml down 2>nul
)

echo.
echo   Done.
echo.

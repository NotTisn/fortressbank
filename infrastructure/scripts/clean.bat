@echo off
setlocal
REM ============================================================================
REM FortressBank - Nuclear Clean (Full Reset)
REM ============================================================================
REM WARNING: This will:
REM   - Kill ALL Java processes
REM   - Stop ALL Docker containers
REM   - Delete ALL target/ directories
REM   - Delete ALL logs
REM ============================================================================

echo.
echo   ================================================================
echo        FORTRESSBANK NUCLEAR CLEAN
echo   ================================================================
echo.
echo   WARNING: This will:
echo     - Kill ALL Java processes
echo     - Stop ALL Docker containers
echo     - Delete ALL target/ directories
echo     - Delete ALL logs
echo.

set /p confirm="   Continue? [y/N]: "
if /i not "%confirm%"=="y" (
    echo   Aborted.
    exit /b 0
)

set "SCRIPT_DIR=%~dp0"
set "INFRA_DIR=%SCRIPT_DIR%.."
set "ROOT=%INFRA_DIR%\.."

echo.
echo [1/4] Killing ALL Java processes...
taskkill /F /IM java.exe >nul 2>&1
timeout /t 2 /nobreak >nul

echo [2/4] Stopping Docker containers...
cd /d "%INFRA_DIR%"
docker compose -f compose-infra-only.yaml down 2>nul

REM Also try the original compose files
cd /d "%ROOT%"
docker compose -f docker-compose.base.yml -f docker-compose.infra.yml -f docker-compose.services.yml down 2>nul

echo [3/4] Deleting target/ directories...
for /d %%d in ("%ROOT%\*") do (
    if exist "%%d\target" (
        echo     Deleting %%~nxd\target
        rd /s /q "%%d\target" 2>nul
    )
)

echo [4/4] Clearing logs...
if exist "%INFRA_DIR%\logs" rd /s /q "%INFRA_DIR%\logs" 2>nul

echo.
echo   ================================================================
echo        Nuclear clean complete!
echo   ================================================================
echo.
echo   To rebuild and start:
echo     cd infrastructure
echo     dev.bat
echo.

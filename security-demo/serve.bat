@echo off
REM ======================================
REM FortressBank Security Demo Launcher
REM ======================================

echo.
echo  ╔═══════════════════════════════════════════════════════════╗
echo  ║       🛡️  FORTRESSBANK SECURITY INCIDENT SIMULATOR        ║
echo  ╚═══════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo  Starting HTTP server on port 3000...
echo.
echo  Dashboard URL: http://localhost:3000
echo.
echo  Press Ctrl+C to stop the server
echo.

REM Try Python 3 first
where python >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo  Using: Python HTTP Server
    python -m http.server 3000
    goto :EOF
)

REM Try Python (might be python3 on some systems)
where python3 >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo  Using: Python3 HTTP Server
    python3 -m http.server 3000
    goto :EOF
)

REM Try Node.js http-server (npm install -g http-server)
where http-server >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo  Using: Node.js http-server
    http-server -p 3000 -c-1
    goto :EOF
)

REM Try npx (comes with Node.js)
where npx >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo  Using: npx http-server
    npx http-server -p 3000 -c-1
    goto :EOF
)

echo.
echo  ERROR: No HTTP server found!
echo.
echo  Please install one of:
echo    - Python: https://python.org
echo    - Node.js: https://nodejs.org (then: npm install -g http-server)
echo.
echo  Or open index.html directly in your browser.
echo.
pause

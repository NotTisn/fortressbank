@echo off
REM ============================================================================
REM FortressBank Dev Mode - Simple wrapper for dev-mode.ps1
REM ============================================================================
REM
REM Usage:
REM   dev.bat              Start all services (logs auto-cleared)
REM   dev.bat -watch       Start all + centralized error watcher
REM   dev.bat -status      Show service status
REM   dev.bat -logs        Open logs folder
REM   dev.bat -clearlogs   Clear logs only (no restart)
REM   dev.bat -kill        Kill all Java processes
REM   dev.bat -clean       Clean all Maven target directories (use after switching branches!)
REM   dev.bat -infra       Start infrastructure only (Docker)
REM   dev.bat -infradown   Stop infrastructure (Docker)
REM
REM NOTE: Logs are ALWAYS cleared on startup. Fresh run = fresh logs.
REM IMPORTANT: After switching git branches, run `dev -clean` first to avoid stale class files!
REM
REM Per copilot-instructions.md - One-command dev experience
REM ============================================================================

powershell -ExecutionPolicy Bypass -File "%~dp0scripts\dev-mode.ps1" %*

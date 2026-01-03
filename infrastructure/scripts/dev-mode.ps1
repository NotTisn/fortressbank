# ============================================================================
# FortressBank Dev Mode - All-in-One Service Launcher
# ============================================================================
# 
# Usage:
#   .\dev-mode.ps1              Start all services (logs auto-cleared)
#   .\dev-mode.ps1 -Watch       Start all + error watcher
#   .\dev-mode.ps1 -Status      Show service status only
#   .\dev-mode.ps1 -Logs        Open logs folder
#   .\dev-mode.ps1 -ClearLogs   Clear logs only (no restart)
#   .\dev-mode.ps1 -Kill        Kill all Java processes
#   .\dev-mode.ps1 -Clean       Clean all Maven target directories
#   .\dev-mode.ps1 -Infra       Start infrastructure only (Docker)
#   .\dev-mode.ps1 -InfraDown   Stop infrastructure (Docker)
#
# NOTE: Logs are ALWAYS cleared on startup. Fresh run = fresh logs.
#       Old logs are noise. Current problems are what matter.
#
# IMPORTANT: After switching git branches, run -Clean first!
#            Stale class files in target/ can cause mysterious startup failures.
#
# Per copilot-instructions.md §7 TERMINAL DISCIPLINE
# ============================================================================

param(
    [switch]$Watch,
    [switch]$Status,
    [switch]$Logs,
    [switch]$ClearLogs,
    [switch]$Kill,
    [switch]$Infra,
    [switch]$InfraDown,
    [switch]$Clean
)

# ============================================================================
# Configuration — FortressBank Specific
# ============================================================================

$ErrorActionPreference = "Stop"

# Calculate paths from this script's location
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$INFRA_DIR = Split-Path -Parent $SCRIPT_DIR
$ROOT_DIR = Split-Path -Parent $INFRA_DIR
$LOG_DIR = Join-Path $INFRA_DIR "logs"

# PROJECT NAME — Used in banner and window titles
$PROJECT_NAME = "FortressBank"

# SERVICE DEFINITIONS — Per copilot-instructions.md §9 ARCHITECTURE
# Note: config-server internal port is 8888, but exposed on 8889 to avoid conflict with Keycloak
$SERVICES = @(
    @{ Name = "config-server";        Dir = "config-server";        Port = 8889;  Type = "core" }
    @{ Name = "discovery";            Dir = "discovery";            Port = 8761;  Type = "core" }
    @{ Name = "user-service";         Dir = "user-service";         Port = 4000;  Type = "service" }
    @{ Name = "account-service";      Dir = "account-service";      Port = 4001;  Type = "service" }
    @{ Name = "notification-service"; Dir = "notification-service"; Port = 4002;  Type = "service" }
    @{ Name = "transaction-service";  Dir = "transaction-service";  Port = 4004;  Type = "service" }
    @{ Name = "audit-service";        Dir = "audit-service";        Port = 4005;  Type = "service" }
    @{ Name = "risk-engine";          Dir = "risk-engine";          Port = 6000;  Type = "service" }
)

# INFRASTRUCTURE PORTS — Docker services to check/start
# Per copilot-instructions.md §ENVIRONMENT_SETUP
$INFRA_PORTS = @{
    userdb          = 5433
    accountdb       = 5434
    transactiondb   = 5435
    auditdb         = 5436
    notificationdb  = 5437
    Keycloak        = 8888
    Redis           = 6379
    RabbitMQ        = 5672
    Kong            = 8000
}

# SERVICE COLORS — For error watcher display
$SERVICE_COLORS = @{
    'config-server'        = 'White'
    'discovery'            = 'DarkCyan'
    'user-service'         = 'Cyan'
    'account-service'      = 'Green'
    'notification-service' = 'Magenta'
    'transaction-service'  = 'Yellow'
    'audit-service'        = 'Blue'
    'risk-engine'          = 'Red'
}

# ============================================================================
# Utility Functions
# ============================================================================

function Test-Port {
    param([int]$Port)
    try {
        $client = New-Object Net.Sockets.TcpClient
        $client.Connect("127.0.0.1", $Port)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Write-Status {
    param([string]$Message, [string]$Type = "INFO")
    $ts = Get-Date -Format "HH:mm:ss"
    switch ($Type) {
        "OK"    { Write-Host "[$ts] " -NoNewline -ForegroundColor DarkGray; Write-Host "[OK] " -NoNewline -ForegroundColor Green; Write-Host $Message }
        "WAIT"  { Write-Host "[$ts] " -NoNewline -ForegroundColor DarkGray; Write-Host "[..] " -NoNewline -ForegroundColor Yellow; Write-Host $Message }
        "ERR"   { Write-Host "[$ts] " -NoNewline -ForegroundColor DarkGray; Write-Host "[!!] " -NoNewline -ForegroundColor Red; Write-Host $Message }
        "INFO"  { Write-Host "[$ts] " -NoNewline -ForegroundColor DarkGray; Write-Host "[--] " -NoNewline -ForegroundColor Cyan; Write-Host $Message }
        default { Write-Host "[$ts] $Message" }
    }
}

function Show-Banner {
    Write-Host ""
    Write-Host "  ================================================================" -ForegroundColor Cyan
    Write-Host "   ____  ___  ____ _____ ____  _____ ____ ____  ____    _    _  _ _  __" -ForegroundColor Cyan
    Write-Host "  |  __\/ _ \|  _ \_   _|  _ \| ____/ ___/ ___|| __ )  / \  | \| | |/ /" -ForegroundColor Cyan
    Write-Host "  | |_ | | | | |_) || | | |_) |  _| \___ \___ \|  _ \ / _ \ | .  |   / " -ForegroundColor Cyan
    Write-Host "  |  _|| |_| |  _ < | | |  _ <| |___ ___) |__) | |_) / ___ \| |\  | |\ \" -ForegroundColor Cyan
    Write-Host "  |_|   \___/|_| \_\|_| |_| \_\_____|____/____/|____/_/   \_\_| \_|_| \_\" -ForegroundColor Cyan
    Write-Host "                                                                      " -ForegroundColor Cyan
    Write-Host "       Dev Mode - Security-First Microservices Platform               " -ForegroundColor DarkGray
    Write-Host "  ================================================================" -ForegroundColor Cyan
    Write-Host ""
}

# ============================================================================
# Command Handlers
# ============================================================================

function Show-ServiceStatus {
    Show-Banner
    
    Write-Host "  Infrastructure Status:" -ForegroundColor White
    Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray
    foreach ($name in $INFRA_PORTS.Keys | Sort-Object) {
        $port = $INFRA_PORTS[$name]
        $up = Test-Port $port
        $portStatus = if ($up) { "OK" } else { "ERR" }
        Write-Status "$($name.PadRight(16)) :$port" $portStatus
    }
    
    Write-Host ""
    Write-Host "  Service Status:" -ForegroundColor White
    Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray
    foreach ($svc in $SERVICES) {
        $up = Test-Port $svc.Port
        $portStatus = if ($up) { "OK" } else { "ERR" }
        Write-Status "$($svc.Name.PadRight(20)) :$($svc.Port)" $portStatus
    }
    Write-Host ""
}

function Open-LogsFolder {
    if (-not (Test-Path $LOG_DIR)) {
        New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
    }
    Start-Process explorer.exe -ArgumentList $LOG_DIR
    Write-Status "Opened logs folder: $LOG_DIR" "OK"
}

function Clear-Logs {
    if (Test-Path $LOG_DIR) {
        Remove-Item -Path "$LOG_DIR\*" -Force -Recurse -ErrorAction SilentlyContinue
        Write-Status "Cleared logs folder" "OK"
    }
}

function Stop-AllJava {
    Write-Status "Stopping all Java processes..." "WAIT"
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Status "All Java processes stopped" "OK"
}

function Clean-AllTargets {
    Write-Status "Cleaning all Maven target directories..." "WAIT"
    
    # Run mvn clean from the root
    Push-Location $ROOT_DIR
    try {
        $result = & mvn clean 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Status "Maven clean completed successfully" "OK"
        } else {
            Write-Status "Maven clean failed - check Maven installation" "ERR"
            Write-Host ($result | Out-String) -ForegroundColor Red
        }
    } catch {
        Write-Status "Maven clean failed: $_" "ERR"
    }
    Pop-Location
}

function Start-InfrastructureOnly {
    Write-Status "Starting Docker infrastructure..." "WAIT"
    $composeFile = Join-Path $INFRA_DIR "compose-infra-only.yaml"
    
    if (-not (Test-Path $composeFile)) {
        Write-Status "compose-infra-only.yaml not found at $composeFile" "ERR"
        return
    }
    
    Push-Location $INFRA_DIR
    docker compose -f compose-infra-only.yaml up -d
    Pop-Location
    
    Write-Status "Infrastructure started. Waiting for services to be healthy..." "WAIT"
    Start-Sleep -Seconds 10
    
    # Show status
    foreach ($name in $INFRA_PORTS.Keys | Sort-Object) {
        $port = $INFRA_PORTS[$name]
        $up = Test-Port $port
        $portStatus = if ($up) { "OK" } else { "WAIT" }
        Write-Status "$($name.PadRight(16)) :$port" $portStatus
    }
}

function Stop-Infrastructure {
    Write-Status "Stopping Docker infrastructure..." "WAIT"
    $composeFile = Join-Path $INFRA_DIR "compose-infra-only.yaml"
    
    Push-Location $INFRA_DIR
    docker compose -f compose-infra-only.yaml down
    Pop-Location
    
    Write-Status "Infrastructure stopped" "OK"
}

# ============================================================================
# Service Launcher — The Cunning Part
# ============================================================================

function Start-ServiceInWindow {
    param(
        [string]$Name,
        [string]$Dir,
        [int]$Port
    )
    
    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    $serviceDir = Join-Path $ROOT_DIR $Dir
    
    if (-not (Test-Path $LOG_DIR)) {
        New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
    }
    $logFile = Join-Path $LOG_DIR "$Name-$timestamp.log"
    
    # The inner script that runs in the new window
    # Per DEV_MODE_BLUEPRINT: Only show ERROR + startup confirmation; everything else → file
    $innerScript = @"
`$ErrorActionPreference = 'Continue'
`$host.UI.RawUI.WindowTitle = '[$Name] ... (port $Port)'

`$logFile = '$logFile'
`$serviceDir = '$serviceDir'
`$serviceName = '$Name'
`$servicePort = $Port
`$errorCount = 0
`$isReady = `$false

# Minimal header
Write-Host ""
Write-Host "  [$Name] port $Port" -ForegroundColor Cyan
Write-Host "  Log: $logFile" -ForegroundColor DarkGray
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host ""

function Process-Line {
    param([string]`$Line)
    
    # Always write to log file
    `$Line | Out-File -FilePath `$logFile -Append -Encoding utf8
    
    # Startup confirmation - show this
    if (`$Line -match 'Started .+ in .+ seconds|Tomcat started|Netty started|JVM running|Application availability state') {
        if (-not `$isReady) {
            `$isReady = `$true
            `$host.UI.RawUI.WindowTitle = "[$Name] OK (port $Port)"
            Write-Host `$Line -ForegroundColor Green
        }
        return
    }
    
    # Error level logs - always show
    if (`$Line -match '\s(ERROR|SEVERE|FATAL)\s' -and `$Line -notmatch 'ErrorCode|ErrorResponse|ExceptionHandler|GlobalException') {
        `$script:errorCount++
        `$host.UI.RawUI.WindowTitle = "[$Name] ERR! ($Port) - `$script:errorCount errors"
        Write-Host `$Line -ForegroundColor Red
        return
    }
    
    # Exception stack traces - show
    if (`$Line -match '^\s+at\s|^Caused by:|Exception in thread|\.Exception:|Error:|BUILD FAILURE|COMPILATION ERROR') {
        Write-Host `$Line -ForegroundColor Red
        return
    }
    
    # Warnings - show in yellow
    if (`$Line -match '\sWARN\s') {
        Write-Host `$Line -ForegroundColor Yellow
        return
    }
    
    # Everything else: SILENT (goes to file only)
}

Set-Location `$serviceDir

# Check if mvnw.cmd exists
if (-not (Test-Path ".\mvnw.cmd")) {
    Write-Host "ERROR: mvnw.cmd not found in `$serviceDir" -ForegroundColor Red
    Read-Host 'Press Enter to close'
    exit 1
}

# Set environment variables for local dev
# These override application.yml and config-server settings
# Per copilot-instructions.md §COMMON_GOTCHAS: Docker hostnames don't work locally

# ============================================================================
# CORE SETTINGS (All services)
# ============================================================================
`$env:SERVER_PORT = `$servicePort

# ============================================================================
# CONFIG-SERVER
# ============================================================================
if (`$serviceName -eq 'config-server') {
    # Config-server IS the config server, it doesn't need to connect to itself
    # It runs on 8889 locally to avoid conflict with Keycloak on 8888
    # Note: config-server's application.yml has server.port=8888, we override to 8889
    `$env:SPRING_CLOUD_CONFIG_ENABLED = "false"
    # Clear any previous values
    `$env:SPRING_CLOUD_CONFIG_URI = `$null
    `$env:SPRING_CONFIG_IMPORT = `$null
}
# ============================================================================
# DISCOVERY (Eureka)
# ============================================================================
elseif (`$serviceName -eq 'discovery') {
    # Discovery's application.yml imports from config-server:8888 (Docker hostname)
    # Override to use localhost:8889
    `$env:SPRING_CONFIG_IMPORT = "optional:configserver:http://localhost:8889"
    `$env:SPRING_CLOUD_CONFIG_URI = "http://localhost:8889"
}
# ============================================================================
# REGULAR SERVICES
# ============================================================================
else {
    # Connect to config-server at localhost:8889
    `$env:SPRING_CONFIG_IMPORT = "optional:configserver:http://localhost:8889"
    `$env:SPRING_CLOUD_CONFIG_URI = "http://localhost:8889"
    
    # Database URLs for local dev (Docker exposes these ports to localhost)
    # Per copilot-instructions.md §ENVIRONMENT_SETUP
    `$env:SPRING_DATASOURCE_URL = switch (`$serviceName) {
        'user-service'         { "jdbc:postgresql://localhost:5433/userdb" }
        'account-service'      { "jdbc:postgresql://localhost:5434/accountdb" }
        'transaction-service'  { "jdbc:postgresql://localhost:5435/transactiondb" }
        'audit-service'        { "jdbc:postgresql://localhost:5436/auditdb" }
        'notification-service' { "jdbc:postgresql://localhost:5437/notificationdb" }
        default { `$null }
    }
    `$env:SPRING_DATASOURCE_USERNAME = "postgres"
    `$env:SPRING_DATASOURCE_PASSWORD = "123456"
    
    # Redis for local (Docker exposes 6379 to localhost)
    `$env:SPRING_DATA_REDIS_HOST = "localhost"
    `$env:SPRING_REDIS_HOST = "localhost"
    `$env:REDIS_HOST = "localhost"
    
    # RabbitMQ for local (Docker exposes 5672 to localhost)
    `$env:SPRING_RABBITMQ_HOST = "localhost"
    `$env:SPRING_RABBITMQ_PORT = "5672"
    `$env:SPRING_RABBITMQ_USERNAME = "guest"
    `$env:SPRING_RABBITMQ_PASSWORD = "guest"
    
    # JWT issuer URI for local (Keycloak on Docker exposes 8888)
    # Per copilot-instructions.md §9 ARCHITECTURE: Keycloak :8888
    `$env:JWT_ISSUER_URI = "http://localhost:8888/realms/fortressbank-realm"
    `$env:SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI = "http://localhost:8888/realms/fortressbank-realm"
    
    # Service-to-service communication URLs (for Feign clients)
    # Override Docker hostnames to localhost with correct ports
    `$env:ACCOUNT_SERVICE_URL = "http://localhost:4001"
    `$env:SERVICES_ACCOUNT_SERVICE_URL = "http://localhost:4001"
}

# ============================================================================
# EUREKA (All services except config-server)
# ============================================================================
if (`$serviceName -ne 'config-server') {
    # Eureka client settings - use localhost instead of Docker hostname
    # Per copilot-instructions.md §9: Discovery (Eureka):8761
    `$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = "http://localhost:8761/eureka/"
    `$env:EUREKA_DEFAULT_ZONE = "http://localhost:8761/eureka/"
    `$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS = "true"
}

# Use 'local' profile - application-local.yml has localhost overrides for all services
# This is simpler and more reliable than complex JVM args
Write-Host "  Using Spring profile: local" -ForegroundColor DarkGray
Write-Host "  Port: `$servicePort" -ForegroundColor DarkGray
Write-Host ""

# Build JVM args - just set port and profile
`$jvmArgs = @()
`$jvmArgs += "-Dserver.port=`$servicePort"

# config-server needs both 'local' and 'native' profiles
if (`$serviceName -eq 'config-server') {
    `$jvmArgs += "-Dspring.profiles.active=local,native"
} else {
    `$jvmArgs += "-Dspring.profiles.active=local"
}

`$env:MAVEN_OPTS = `$jvmArgs -join " "
Write-Host "  MAVEN_OPTS: `$env:MAVEN_OPTS" -ForegroundColor DarkGray
Write-Host ""

# Run Maven with spring-boot:run, passing JVM args
# Per copilot-instructions.md §7: mvn spring-boot:run for dev
Write-Host "  Starting: mvnw.cmd spring-boot:run" -ForegroundColor DarkGray
Write-Host ""

`$jvmArgsStr = `$jvmArgs -join " "
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=`$jvmArgsStr" 2>&1 | ForEach-Object { Process-Line `$_ }

# On exit, show summary
`$exitCode = `$LASTEXITCODE
Write-Host ""
if (`$exitCode -ne 0) {
    Write-Host "=== CRASHED (exit `$exitCode) ===" -ForegroundColor Red
    Write-Host ""
    Write-Host "Last 30 lines of log:" -ForegroundColor Yellow
    if (Test-Path `$logFile) {
        Get-Content `$logFile -Tail 30 | ForEach-Object { Write-Host `$_ -ForegroundColor DarkGray }
    }
} else {
    Write-Host "=== STOPPED ===" -ForegroundColor Yellow
}
Write-Host ""
Read-Host 'Press Enter to close'
"@
    
    # Encode and launch
    $bytes = [System.Text.Encoding]::Unicode.GetBytes($innerScript)
    $encoded = [Convert]::ToBase64String($bytes)
    
    Start-Process powershell -ArgumentList "-NoExit", "-EncodedCommand", $encoded
    
    Write-Status "$Name starting on $Port (log: $Name-$timestamp.log)" "WAIT"
}

# ============================================================================
# Error Watcher — Centralized Error View
# ============================================================================

function Start-ErrorWatcher {
    if (-not (Test-Path $LOG_DIR)) {
        New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
    }
    
    # Build colors string for embedding
    $colorsString = ($SERVICE_COLORS.GetEnumerator() | ForEach-Object { "'$($_.Key)' = '$($_.Value)'" }) -join "; "
    
    $watcherScript = @"
`$host.UI.RawUI.WindowTitle = 'FortressBank Errors'
`$LogDir = '$LOG_DIR'

Write-Host ''
Write-Host '  ================================================================' -ForegroundColor Red
Write-Host '       FORTRESSBANK ERROR WATCHER                                 ' -ForegroundColor Red
Write-Host '  ================================================================' -ForegroundColor Red
Write-Host "  Logs: `$LogDir" -ForegroundColor DarkGray
Write-Host '  Shows: ERROR level logs, Exceptions, Stack traces, Build failures' -ForegroundColor DarkGray
Write-Host '  ----------------------------------------------------------------' -ForegroundColor DarkGray
Write-Host ''

`$filePositions = @{}

`$serviceColors = @{
    $colorsString
}

while (`$true) {
    `$files = Get-ChildItem -Path `$LogDir -Filter '*.log' -ErrorAction SilentlyContinue
    
    foreach (`$file in `$files) {
        `$path = `$file.FullName
        `$currentSize = `$file.Length
        
        if (-not `$filePositions.ContainsKey(`$path)) {
            `$filePositions[`$path] = 0
        }
        
        `$lastPos = `$filePositions[`$path]
        
        if (`$currentSize -gt `$lastPos) {
            # Extract service name from filename
            `$serviceName = `$file.BaseName -replace '-\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}$', ''
            `$color = if (`$serviceColors.ContainsKey(`$serviceName)) { `$serviceColors[`$serviceName] } else { 'White' }
            
            # Read new content using FileShare.ReadWrite to avoid locking
            try {
                `$fs = [System.IO.FileStream]::new(`$path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
                `$reader = [System.IO.StreamReader]::new(`$fs)
                `$reader.BaseStream.Seek(`$lastPos, [System.IO.SeekOrigin]::Begin) | Out-Null
                `$reader.DiscardBufferedData()
                
                while (`$null -ne (`$line = `$reader.ReadLine())) {
                    # Only show errors and exceptions
                    if (`$line -match '\s(ERROR|SEVERE|FATAL)\s' -and `$line -notmatch 'ErrorCode|ErrorResponse|ExceptionHandler') {
                        `$ts = Get-Date -Format 'HH:mm:ss'
                        Write-Host "[`$ts] " -NoNewline -ForegroundColor DarkGray
                        Write-Host "[$serviceName] " -NoNewline -ForegroundColor `$color
                        Write-Host `$line -ForegroundColor Red
                    }
                    elseif (`$line -match '^\s+at\s|^Caused by:|Exception in thread|BUILD FAILURE|COMPILATION ERROR') {
                        Write-Host "         `$line" -ForegroundColor DarkRed
                    }
                }
                
                `$filePositions[`$path] = `$reader.BaseStream.Position
                `$reader.Close()
                `$fs.Close()
            } catch {
                # File might be temporarily locked, skip this iteration
            }
        }
    }
    
    Start-Sleep -Milliseconds 300
}
"@
    
    $bytes = [System.Text.Encoding]::Unicode.GetBytes($watcherScript)
    $encoded = [Convert]::ToBase64String($bytes)
    
    Start-Process powershell -ArgumentList "-NoExit", "-EncodedCommand", $encoded
    
    Write-Status "Error watcher started" "OK"
}

# ============================================================================
# Main Flow
# ============================================================================

# Handle simple commands first
if ($Status) {
    Show-ServiceStatus
    exit 0
}

if ($Logs) {
    Open-LogsFolder
    exit 0
}

if ($Kill) {
    Stop-AllJava
    exit 0
}

if ($ClearLogs) {
    Clear-Logs
    exit 0
}

if ($Infra) {
    Show-Banner
    Start-InfrastructureOnly
    exit 0
}

if ($InfraDown) {
    Show-Banner
    Stop-Infrastructure
    exit 0
}

if ($Clean) {
    Show-Banner
    Clean-AllTargets
    exit 0
}

# ALWAYS clear old logs on startup - fresh run = fresh logs
# (user can still use -ClearLogs to clear without starting, or -Logs to view)
Clear-Logs

# Main startup flow
Show-Banner

# Step 1: Check Docker infrastructure
Write-Host "  [1/5] Infrastructure Check" -ForegroundColor White
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray

# Check if Redis is up (simplest infra check)
$infraUp = Test-Port 6379
if (-not $infraUp) {
    Write-Status "Infrastructure not running. Starting Docker..." "WAIT"
    Start-InfrastructureOnly
    
    # Wait for Keycloak (the slowest to start)
    Write-Status "Waiting for Keycloak to be ready (this takes 2-3 minutes on first boot)..." "WAIT"
    $maxWait = 180  # 3 minutes
    $waited = 0
    while (-not (Test-Port 8888) -and $waited -lt $maxWait) {
        Start-Sleep -Seconds 5
        $waited += 5
        Write-Host "." -NoNewline
    }
    Write-Host ""
    
    if ($waited -ge $maxWait) {
        Write-Status "Keycloak not ready after 3 minutes. Check Docker logs." "ERR"
    } else {
        Write-Status "Keycloak ready" "OK"
    }
}

# Show infra status
foreach ($name in $INFRA_PORTS.Keys | Sort-Object) {
    $port = $INFRA_PORTS[$name]
    $up = Test-Port $port
    $portStatus = if ($up) { "OK" } else { "ERR" }
    Write-Status "$($name.PadRight(16)) :$port" $portStatus
}

Write-Host ""

# Step 2: Create logs directory
Write-Host "  [2/5] Logs Directory" -ForegroundColor White
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray
if (-not (Test-Path $LOG_DIR)) {
    New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
}
Write-Status "Log directory: $LOG_DIR" "OK"
Write-Host ""

# Step 3: Start core services SEQUENTIALLY (config-server THEN discovery)
Write-Host "  [3/5] Core Services (sequential startup)" -ForegroundColor White
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray

# Start config-server FIRST and wait for it
$configServer = $SERVICES | Where-Object { $_.Name -eq "config-server" }
if ($configServer) {
    Start-ServiceInWindow -Name $configServer.Name -Dir $configServer.Dir -Port $configServer.Port
    
    Write-Status "Waiting for config-server to be ready (required by other services)..." "WAIT"
    $maxWait = 120
    $waited = 0
    while (-not (Test-Port 8889) -and $waited -lt $maxWait) {
        Start-Sleep -Seconds 3
        $waited += 3
    }
    if (Test-Port 8889) {
        Write-Status "config-server ready on :8889" "OK"
    } else {
        Write-Status "config-server not ready after 2 minutes - other services may fail" "ERR"
    }
}

# THEN start discovery and wait for it
$discovery = $SERVICES | Where-Object { $_.Name -eq "discovery" }
if ($discovery) {
    Start-ServiceInWindow -Name $discovery.Name -Dir $discovery.Dir -Port $discovery.Port
    
    Write-Status "Waiting for Eureka (discovery) to be ready..." "WAIT"
    $maxWait = 120
    $waited = 0
    while (-not (Test-Port 8761) -and $waited -lt $maxWait) {
        Start-Sleep -Seconds 3
        $waited += 3
    }
    if (Test-Port 8761) {
        Write-Status "Eureka ready on :8761" "OK"
    } else {
        Write-Status "Eureka not ready after 2 minutes" "ERR"
    }
}

Write-Host ""

# Step 4: Start other services
Write-Host "  [4/5] Application Services" -ForegroundColor White
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray

$appServices = $SERVICES | Where-Object { $_.Type -eq "service" }
foreach ($svc in $appServices) {
    Start-ServiceInWindow -Name $svc.Name -Dir $svc.Dir -Port $svc.Port
    Start-Sleep -Milliseconds 1500  # Stagger launches to reduce CPU spike
}

Write-Host ""

# Step 5: Error watcher (optional)
Write-Host "  [5/5] Error Watcher" -ForegroundColor White
Write-Host "  ----------------------------------------------------------------" -ForegroundColor DarkGray

if ($Watch) {
    Start-ErrorWatcher
} else {
    Write-Status "Skipped. Use -Watch flag to enable." "INFO"
}

# Final summary
Write-Host ""
Write-Host "  ================================================================" -ForegroundColor Green
Write-Host "       FortressBank Dev Mode Active!                              " -ForegroundColor Green
Write-Host "  ================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Services (starting up - give them 30-60s):" -ForegroundColor White
foreach ($svc in $SERVICES) {
    Write-Host "    $($svc.Name.PadRight(22)) http://localhost:$($svc.Port)" -ForegroundColor Gray
}
Write-Host ""
Write-Host "  Key URLs:" -ForegroundColor White
Write-Host "    Kong Gateway          http://localhost:8000" -ForegroundColor Gray
Write-Host "    Keycloak Admin        http://localhost:8888  (admin/admin)" -ForegroundColor Gray
Write-Host "    Eureka Dashboard      http://localhost:8761" -ForegroundColor Gray
Write-Host "    RabbitMQ Management   http://localhost:15672 (guest/guest)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Logs folder:            $LOG_DIR" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Commands:" -ForegroundColor White
Write-Host "    .\dev.bat -status     Check all services" -ForegroundColor DarkGray
Write-Host "    .\dev.bat -logs       Open logs folder" -ForegroundColor DarkGray
Write-Host "    .\dev.bat -kill       Stop all Java services" -ForegroundColor DarkGray
Write-Host "    .\dev.bat -clean      Clean all target dirs (after branch switch!)" -ForegroundColor DarkGray
Write-Host "    .\dev.bat -infra      Start infrastructure only" -ForegroundColor DarkGray
Write-Host "    .\dev.bat -infradown  Stop infrastructure" -ForegroundColor DarkGray
Write-Host ""

# Session Security Tests
# Test 01: Concurrent Session Abuse
# An attacker uses stolen credentials from multiple locations simultaneously

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$KeycloakUrl = "http://localhost:8888"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SESSION SECURITY TEST 01" -ForegroundColor Cyan
Write-Host "Concurrent Session Abuse" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

$AllSecure = $true

# Test 1: Acquire multiple tokens for same user
Write-Host "[*] Test 1: Multiple simultaneous sessions" -ForegroundColor Yellow

$Tokens = @()
$SessionCount = 5

Write-Host "[*] Attempting to create $SessionCount simultaneous sessions..." -ForegroundColor Gray

for ($i = 1; $i -le $SessionCount; $i++) {
    try {
        $Body = @{
            grant_type = "password"
            client_id = "kong"
            client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
            username = "testuser"
            password = "password"
        }
        
        $Response = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
            -Method POST -Body $Body -ContentType "application/x-www-form-urlencoded"
        
        $Tokens += $Response.access_token
        Write-Host "    [+] Session $i created" -ForegroundColor Gray
    } catch {
        Write-Host "    [-] Session $i failed: $_" -ForegroundColor Red
    }
}

if ($Tokens.Count -eq $SessionCount) {
    Write-Host "[!] WARNING: All $SessionCount sessions created successfully" -ForegroundColor Yellow
    Write-Host "    Recommendation: Implement max concurrent sessions limit" -ForegroundColor Yellow
} else {
    Write-Host "[+] Session limiting detected ($($Tokens.Count)/$SessionCount sessions)" -ForegroundColor Green
}

# Test 2: Use old token after new login
Write-Host "`n[*] Test 2: Old token validity after new login" -ForegroundColor Yellow

if ($Tokens.Count -ge 2) {
    $OldToken = $Tokens[0]
    $NewToken = $Tokens[-1]
    
    # Try to use the oldest token
    $Headers = @{
        "Authorization" = "Bearer $OldToken"
        "Content-Type" = "application/json"
    }
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $Headers
        Write-Host "[!] WARNING: Old token still valid after new login" -ForegroundColor Yellow
        Write-Host "    Recommendation: Revoke previous tokens on new login" -ForegroundColor Yellow
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -eq 401) {
            Write-Host "[+] PASS: Old token invalidated ($StatusCode)" -ForegroundColor Green
        } else {
            Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
        }
    }
}

# Test 3: Session from different "locations" 
Write-Host "`n[*] Test 3: Geographically impossible sessions" -ForegroundColor Yellow
Write-Host "[*] Simulating requests with different X-Forwarded-For headers..." -ForegroundColor Gray

$Locations = @(
    "203.0.113.1",   # US
    "198.51.100.1",  # Europe
    "192.0.2.1"      # Asia
)

$Token = $Tokens[0]
$Headers = @{
    "Authorization" = "Bearer $Token"
    "Content-Type" = "application/json"
}

foreach ($IP in $Locations) {
    $TestHeaders = $Headers.Clone()
    $TestHeaders["X-Forwarded-For"] = $IP
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $TestHeaders
        Write-Host "    [!] Request accepted from $IP" -ForegroundColor Yellow
    } catch {
        Write-Host "    [+] Request blocked from $IP" -ForegroundColor Green
    }
}

Write-Host "[*] Note: Geo-velocity checks should flag rapid location changes" -ForegroundColor Gray

# Test 4: Device fingerprint bypass
Write-Host "`n[*] Test 4: Device fingerprint consistency" -ForegroundColor Yellow

$UserAgents = @(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)",
    "curl/7.68.0"
)

foreach ($UA in $UserAgents) {
    $TestHeaders = $Headers.Clone()
    $TestHeaders["User-Agent"] = $UA
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $TestHeaders
        Write-Host "    [*] Accepted User-Agent: $($UA.Substring(0, 40))..." -ForegroundColor Gray
    } catch {
        Write-Host "    [+] Blocked User-Agent change" -ForegroundColor Green
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n[*] RECOMMENDATIONS:" -ForegroundColor Yellow
Write-Host "    1. Limit concurrent sessions per user (e.g., max 3)" -ForegroundColor White
Write-Host "    2. Revoke old tokens on new login (single active session)" -ForegroundColor White
Write-Host "    3. Implement geo-velocity detection (impossible travel)" -ForegroundColor White
Write-Host "    4. Track device fingerprints and alert on changes" -ForegroundColor White

# Session Security Tests
# Test 03: Refresh Token Abuse
# Tests for refresh token security vulnerabilities

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$KeycloakUrl = "http://localhost:8888"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SESSION SECURITY TEST 03" -ForegroundColor Cyan
Write-Host "Refresh Token Abuse" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# First, get a valid token pair
Write-Host "[*] Acquiring token pair..." -ForegroundColor Yellow

try {
    $Body = @{
        grant_type = "password"
        client_id = "kong"
        client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
        username = "testuser"
        password = "password"
    }
    
    $TokenResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
        -Method POST -Body $Body -ContentType "application/x-www-form-urlencoded"
    
    $AccessToken = $TokenResponse.access_token
    $RefreshToken = $TokenResponse.refresh_token
    
    Write-Host "[+] Access token acquired (expires in $($TokenResponse.expires_in)s)" -ForegroundColor Green
    Write-Host "[+] Refresh token acquired" -ForegroundColor Green
} catch {
    Write-Host "[-] Failed to acquire tokens: $_" -ForegroundColor Red
    exit 1
}

$VulnerabilitiesFound = 0

# Test 1: Refresh token reuse after rotation
Write-Host "`n[*] Test 1: Refresh Token Reuse After Rotation" -ForegroundColor Yellow

# Use refresh token to get new tokens
Write-Host "[*] Using refresh token to rotate..." -ForegroundColor Gray

try {
    $RefreshBody = @{
        grant_type = "refresh_token"
        client_id = "kong"
        client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
        refresh_token = $RefreshToken
    }
    
    $NewTokens = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
        -Method POST -Body $RefreshBody -ContentType "application/x-www-form-urlencoded"
    
    Write-Host "[+] Token rotation successful" -ForegroundColor Green
    $NewRefreshToken = $NewTokens.refresh_token
} catch {
    Write-Host "[-] Token rotation failed: $_" -ForegroundColor Red
    exit 1
}

# Now try to reuse the OLD refresh token
Write-Host "[*] Attempting to reuse OLD refresh token..." -ForegroundColor Gray
Start-Sleep -Seconds 1

try {
    $ReuseBody = @{
        grant_type = "refresh_token"
        client_id = "kong"
        client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
        refresh_token = $RefreshToken  # OLD token
    }
    
    $ReuseResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
        -Method POST -Body $ReuseBody -ContentType "application/x-www-form-urlencoded"
    
    Write-Host "[!] VULNERABILITY: Old refresh token still valid!" -ForegroundColor Red
    Write-Host "    Refresh token rotation is NOT invalidating old tokens" -ForegroundColor Red
    Write-Host "    Risk: Stolen refresh tokens remain usable indefinitely" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    Write-Host "[+] PASS: Old refresh token rejected (proper rotation)" -ForegroundColor Green
}

# Test 2: Refresh token from different IP
Write-Host "`n[*] Test 2: Refresh Token IP Binding" -ForegroundColor Yellow

$Headers = @{
    "Content-Type" = "application/x-www-form-urlencoded"
    "X-Forwarded-For" = "1.2.3.4"  # Fake IP
}

try {
    $Response = Invoke-WebRequest -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
        -Method POST -Body $RefreshBody -Headers $Headers
    
    Write-Host "[!] WARNING: Refresh token accepted from different IP" -ForegroundColor Yellow
    Write-Host "    Recommendation: Bind refresh tokens to client IP" -ForegroundColor Yellow
} catch {
    Write-Host "[+] Refresh token IP binding enforced" -ForegroundColor Green
}

# Test 3: Refresh token after logout
Write-Host "`n[*] Test 3: Refresh Token After Logout" -ForegroundColor Yellow

# Logout the session
Write-Host "[*] Logging out session..." -ForegroundColor Gray

try {
    $LogoutBody = @{
        client_id = "kong"
        client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
        refresh_token = $NewRefreshToken
    }
    
    Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/logout" `
        -Method POST -Body $LogoutBody -ContentType "application/x-www-form-urlencoded"
    
    Write-Host "[+] Logout successful" -ForegroundColor Green
} catch {
    Write-Host "[?] Logout endpoint response: $_" -ForegroundColor Yellow
}

# Try to use the refresh token after logout
Write-Host "[*] Attempting to use refresh token after logout..." -ForegroundColor Gray
Start-Sleep -Seconds 1

try {
    $PostLogoutBody = @{
        grant_type = "refresh_token"
        client_id = "kong"
        client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
        refresh_token = $NewRefreshToken
    }
    
    $PostLogoutResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
        -Method POST -Body $PostLogoutBody -ContentType "application/x-www-form-urlencoded"
    
    Write-Host "[!] CRITICAL: Refresh token valid after logout!" -ForegroundColor Red
    Write-Host "    Sessions are NOT properly terminated" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    Write-Host "[+] PASS: Refresh token invalidated after logout" -ForegroundColor Green
}

# Test 4: Refresh token brute force
Write-Host "`n[*] Test 4: Refresh Token Brute Force Protection" -ForegroundColor Yellow

$FailedAttempts = 0
$RateLimited = $false

for ($i = 1; $i -le 10; $i++) {
    $FakeToken = "fake_refresh_token_attempt_$i"
    
    try {
        $BruteBody = @{
            grant_type = "refresh_token"
            client_id = "kong"
            client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
            refresh_token = $FakeToken
        }
        
        $Response = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
            -Method POST -Body $BruteBody -ContentType "application/x-www-form-urlencoded"
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        $FailedAttempts++
        
        if ($StatusCode -eq 429) {
            Write-Host "[+] Rate limited at attempt $i (429)" -ForegroundColor Green
            $RateLimited = $true
            break
        }
    }
}

if (-not $RateLimited) {
    Write-Host "[!] WARNING: No rate limiting on refresh token endpoint" -ForegroundColor Yellow
    Write-Host "    Made $FailedAttempts failed attempts without blocking" -ForegroundColor Yellow
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Refresh token security is properly configured" -ForegroundColor Green
}

Write-Host "`n[*] BEST PRACTICES:" -ForegroundColor Yellow
Write-Host "    1. Rotate refresh tokens on every use" -ForegroundColor White
Write-Host "    2. Invalidate old tokens immediately on rotation" -ForegroundColor White
Write-Host "    3. Bind refresh tokens to client fingerprint (IP, User-Agent)" -ForegroundColor White
Write-Host "    4. Invalidate all tokens on logout" -ForegroundColor White
Write-Host "    5. Rate limit token endpoint" -ForegroundColor White
Write-Host "    6. Short refresh token lifetime (24h max)" -ForegroundColor White

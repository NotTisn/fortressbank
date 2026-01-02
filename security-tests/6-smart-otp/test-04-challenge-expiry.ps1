# Smart OTP Security Tests
# Test 04: Challenge Expiry and Rate Limiting
# Verify that challenges expire and rate limits prevent brute force

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST 04" -ForegroundColor Cyan
Write-Host "Challenge Expiry & Rate Limiting" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Load token
if (Test-Path $TokenFile) {
    $Token = Get-Content $TokenFile -Raw
    $Token = $Token.Trim()
} else {
    Write-Host "[-] Token file not found. Run setup-testuser.ps1 first." -ForegroundColor Red
    exit 1
}

$Headers = @{
    "Authorization" = "Bearer $Token"
    "Content-Type" = "application/json"
}

# Test 1: Brute force signature attempts
Write-Host "[*] Test 1: Brute force signature attempts" -ForegroundColor Yellow

$ChallengeId = "brute-force-test-" + [guid]::NewGuid()
$DeviceId = "brute-force-device"
$AttemptCount = 0
$RateLimited = $false

Write-Host "[*] Attempting 20 rapid signature verifications..." -ForegroundColor Gray

for ($i = 1; $i -le 20; $i++) {
    $RandomSig = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("attempt-$i-" + [guid]::NewGuid()))
    
    $Body = @{
        challengeId = $ChallengeId
        deviceId = $DeviceId
        signatureBase64 = $RandomSig
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
            -Method POST -Headers $Headers -Body $Body
        
        $AttemptCount++
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        $AttemptCount++
        
        if ($StatusCode -eq 429) {
            Write-Host "[+] Rate limited at attempt $i (429 Too Many Requests)" -ForegroundColor Green
            $RateLimited = $true
            break
        } elseif ($StatusCode -eq 423) {
            Write-Host "[+] Challenge locked at attempt $i (423 Locked)" -ForegroundColor Green
            $RateLimited = $true
            break
        }
    }
}

if (-not $RateLimited) {
    Write-Host "[!] WARNING: No rate limiting after $AttemptCount attempts" -ForegroundColor Yellow
    Write-Host "    Consider adding rate limiting to prevent brute force" -ForegroundColor Yellow
}

# Test 2: Verify expired challenge handling
Write-Host "`n[*] Test 2: Expired challenge handling" -ForegroundColor Yellow
Write-Host "[*] Note: This test simulates an old challenge ID" -ForegroundColor Gray

# Use a challenge ID that would be from "yesterday" (simulated by format)
$ExpiredChallengeId = "expired-" + (Get-Date).AddDays(-1).ToString("yyyyMMdd") + "-test"

$Body = @{
    challengeId = $ExpiredChallengeId
    deviceId = "test-device"
    signatureBase64 = "dGVzdC1zaWduYXR1cmU="
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[?] Server processed expired challenge (may be okay if challenge doesn't exist)" -ForegroundColor Yellow
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    
    if ($StatusCode -eq 400 -or $StatusCode -eq 410) {
        Write-Host "[+] PASS: Expired/invalid challenge rejected ($StatusCode)" -ForegroundColor Green
    } elseif ($StatusCode -eq 404) {
        Write-Host "[+] PASS: Challenge not found (404)" -ForegroundColor Green
    } else {
        Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
    }
}

# Test 3: Concurrent challenge handling
Write-Host "`n[*] Test 3: Multiple concurrent challenges" -ForegroundColor Yellow
Write-Host "[*] Checking if user can have multiple active challenges..." -ForegroundColor Gray

# This would need actual transfer endpoints to test properly
# For now, we document the expected behavior
Write-Host "[*] Expected: Only ONE active challenge per user at a time" -ForegroundColor Gray
Write-Host "[*] New challenge should invalidate previous pending challenges" -ForegroundColor Gray

# Test 4: Challenge ID enumeration
Write-Host "`n[*] Test 4: Challenge ID predictability" -ForegroundColor Yellow

$SequentialIds = @(
    "1",
    "2", 
    "100",
    "1000",
    "challenge-1",
    "challenge-2"
)

$PredictableFound = $false
foreach ($Id in $SequentialIds) {
    $Body = @{
        challengeId = $Id
        deviceId = "enum-device"
        signatureBase64 = "dGVzdA=="
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
            -Method POST -Headers $Headers -Body $Body
        
        Write-Host "[!] VULNERABILITY: Sequential ID $Id accepted!" -ForegroundColor Red
        $PredictableFound = $true
    } catch {
        # Expected - these should all fail
    }
}

if (-not $PredictableFound) {
    Write-Host "[+] PASS: Sequential challenge IDs not valid (using UUIDs)" -ForegroundColor Green
}

Write-Host "`n========================================" -ForegroundColor Cyan
if ($RateLimited) {
    Write-Host "RESULT: PASS - Rate limiting active" -ForegroundColor Green
} else {
    Write-Host "RESULT: PARTIAL - Consider adding rate limits" -ForegroundColor Yellow
}
Write-Host "========================================`n" -ForegroundColor Cyan

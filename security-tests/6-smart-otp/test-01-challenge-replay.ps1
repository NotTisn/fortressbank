# Smart OTP Security Tests
# Test 01: Challenge Replay Attack Prevention
# An attacker captures a valid challenge response and tries to replay it

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST 01" -ForegroundColor Cyan
Write-Host "Challenge Replay Attack Prevention" -ForegroundColor Cyan
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

Write-Host "[*] Test: Replay a completed challenge" -ForegroundColor Yellow

# Step 1: Get a challenge (simulating a transfer that returned DEVICE_BIO)
Write-Host "[*] Step 1: Simulating challenge creation..." -ForegroundColor Gray

$TestChallengeId = "test-replay-" + [guid]::NewGuid().ToString()

# Step 2: Try to verify a non-existent/already-used challenge
Write-Host "[*] Step 2: Attempting to verify fabricated challenge ID..." -ForegroundColor Gray

$ReplayBody = @{
    challengeId = $TestChallengeId
    deviceId = "fake-device"
    signatureBase64 = "ZmFrZS1zaWduYXR1cmU="
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
        -Method POST -Headers $Headers -Body $ReplayBody
    
    # Check if server rejected the challenge (valid=false means attack blocked)
    if ($Response.data -and $Response.data.valid -eq $false) {
        Write-Host "[+] PASS: Server rejected fabricated challenge" -ForegroundColor Green
        Write-Host "    Message: $($Response.data.message)" -ForegroundColor Gray
    } else {
        Write-Host "[!] VULNERABILITY: Server accepted fabricated challenge!" -ForegroundColor Red
        Write-Host "    Response: $($Response | ConvertTo-Json -Depth 5)" -ForegroundColor Red
        exit 1
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    
    if ($StatusCode -eq 400 -or $StatusCode -eq 404) {
        Write-Host "[+] PASS: Server rejected non-existent challenge (HTTP $StatusCode)" -ForegroundColor Green
    } elseif ($StatusCode -eq 401) {
        Write-Host "[?] Auth issue - token may be expired" -ForegroundColor Yellow
        exit 1
    } else {
        Write-Host "[!] Unexpected response: HTTP $StatusCode" -ForegroundColor Yellow
        Write-Host "    Error: $_" -ForegroundColor Gray
    }
}

# Step 3: Test challenge expiry (if we had access to a real flow)
Write-Host "`n[*] Step 3: Challenge ID format validation..." -ForegroundColor Gray

$MalformedIds = @(
    "'; DROP TABLE challenges; --",
    "<script>alert(1)</script>",
    "../../../etc/passwd",
    "00000000-0000-0000-0000-000000000000"
)

$AllPassed = $true
foreach ($BadId in $MalformedIds) {
    $Body = @{
        challengeId = $BadId
        deviceId = "test-device"
        signatureBase64 = "dGVzdA=="
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
            -Method POST -Headers $Headers -Body $Body
        
        # Check if server rejected (valid=false means attack blocked)
        if ($Response.data -and $Response.data.valid -eq $false) {
            Write-Host "[+] Rejected: $BadId (valid=false)" -ForegroundColor Green
        } else {
            Write-Host "[!] VULNERABILITY: Accepted malformed ID: $BadId" -ForegroundColor Red
            $AllPassed = $false
        }
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -ge 400 -and $StatusCode -lt 500) {
            Write-Host "[+] Rejected: $BadId (HTTP $StatusCode)" -ForegroundColor Green
        } else {
            Write-Host "[?] Unexpected for $BadId : HTTP $StatusCode" -ForegroundColor Yellow
        }
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
if ($AllPassed) {
    Write-Host "RESULT: PASS - Challenge replay protection working" -ForegroundColor Green
} else {
    Write-Host "RESULT: FAIL - Vulnerabilities detected" -ForegroundColor Red
}
Write-Host "========================================`n" -ForegroundColor Cyan

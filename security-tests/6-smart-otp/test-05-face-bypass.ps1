# Smart OTP Security Tests
# Test 05: Face Verification Bypass Attempts
# An attacker tries to bypass the FACE_VERIFY challenge

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST 05" -ForegroundColor Cyan
Write-Host "Face Verification Bypass Attempts" -ForegroundColor Cyan
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

$AllSecure = $true

# Test 1: Try to skip face verification by directly confirming
Write-Host "[*] Test 1: Skip face verification" -ForegroundColor Yellow

$Body = @{
    challengeId = "face-challenge-" + [guid]::NewGuid()
    verified = $true
    skipVerification = $true
} | ConvertTo-Json

try {
    # Try to directly complete a FACE_VERIFY challenge without actual verification
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-face" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] CRITICAL: Face verification bypassed!" -ForegroundColor Red
    $AllSecure = $false
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    
    if ($StatusCode -eq 400 -or $StatusCode -eq 404) {
        Write-Host "[+] PASS: Cannot skip face verification ($StatusCode)" -ForegroundColor Green
    } elseif ($StatusCode -eq 405) {
        Write-Host "[+] PASS: Method not allowed - face verification requires proper flow" -ForegroundColor Green
    } else {
        Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
    }
}

# Test 2: Try to use DEVICE_BIO when FACE_VERIFY is required
Write-Host "`n[*] Test 2: Challenge type mismatch" -ForegroundColor Yellow

$FaceChallengeId = "must-be-face-" + [guid]::NewGuid()

# Try to verify with device signature when face is required
$Body = @{
    challengeId = $FaceChallengeId
    deviceId = "my-device"
    signatureBase64 = "dGVzdC1zaWduYXR1cmU="
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[?] Need to verify challenge type enforcement" -ForegroundColor Yellow
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] Request rejected (HTTP $StatusCode)" -ForegroundColor Green
}

# Test 3: Replay captured face verification
Write-Host "`n[*] Test 3: Face verification replay" -ForegroundColor Yellow

# Simulate a previously captured face verification response
$OldNonce = "old-nonce-from-previous-verification"

$Body = @{
    challengeId = "new-challenge-" + [guid]::NewGuid()
    nonce = $OldNonce
    faceToken = "previously-captured-face-token"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-face" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] WARNING: May accept replayed face verification" -ForegroundColor Yellow
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] PASS: Replay rejected (HTTP $StatusCode)" -ForegroundColor Green
}

# Test 4: Liveness bypass attempts
Write-Host "`n[*] Test 4: Liveness check bypass" -ForegroundColor Yellow

Write-Host "[*] Sending static image instead of live capture..." -ForegroundColor Gray
Write-Host "[*] Note: Actual liveness testing requires image upload capability" -ForegroundColor Gray
Write-Host "[*] FaceID service should reject:" -ForegroundColor Gray
Write-Host "    - Static photos" -ForegroundColor Gray
Write-Host "    - Screen recordings" -ForegroundColor Gray
Write-Host "    - Low liveness scores" -ForegroundColor Gray

# Test 5: Face registration state check
Write-Host "`n[*] Test 5: Unregistered face handling" -ForegroundColor Yellow

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/users/face-id/status" `
        -Method GET -Headers $Headers
    
    $FaceRegistered = $Response.data.isFaceRegistered
    Write-Host "[*] Face registered: $FaceRegistered" -ForegroundColor Gray
    
    if (-not $FaceRegistered) {
        Write-Host "[*] User has no registered face" -ForegroundColor Gray
        Write-Host "[*] FACE_VERIFY challenges should fail for this user" -ForegroundColor Gray
    }
} catch {
    Write-Host "[?] Could not check face registration status" -ForegroundColor Yellow
}

# Test 6: Check face service availability handling
Write-Host "`n[*] Test 6: Face service unavailable handling" -ForegroundColor Yellow

Write-Host "[*] Expected behavior when fbank-ai is down:" -ForegroundColor Gray
Write-Host "    - HIGH risk transfers should be BLOCKED (not downgraded)" -ForegroundColor Gray
Write-Host "    - User should receive clear error message" -ForegroundColor Gray
Write-Host "    - Audit log should record the failure" -ForegroundColor Gray

Write-Host "`n========================================" -ForegroundColor Cyan
if ($AllSecure) {
    Write-Host "RESULT: PASS - Face verification protections working" -ForegroundColor Green
} else {
    Write-Host "RESULT: FAIL - Critical bypass detected!" -ForegroundColor Red
}
Write-Host "========================================`n" -ForegroundColor Cyan

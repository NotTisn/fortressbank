# Business Logic Attacks
# Test 02: Transaction State Machine Abuse
# Attempts to manipulate transaction state transitions illegally

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "BUSINESS LOGIC TEST 02" -ForegroundColor Cyan
Write-Host "Transaction State Machine Abuse" -ForegroundColor Cyan
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

Write-Host "[*] Transaction states: PENDING -> PENDING_OTP -> COMPLETED/FAILED" -ForegroundColor Yellow
Write-Host "[*] Testing illegal state transitions..." -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Test 1: Verify OTP on non-existent transaction
Write-Host "`n[*] Test 1: OTP on Non-Existent Transaction" -ForegroundColor Yellow

$FakeTransactionId = [guid]::NewGuid().ToString()

$Body = @{
    transactionId = $FakeTransactionId
    otpCode = "123456"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/verify-otp" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] CRITICAL: OTP verification accepted for fake transaction!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 404 -or $StatusCode -eq 400) {
        Write-Host "[+] PASS: Non-existent transaction rejected ($StatusCode)" -ForegroundColor Green
    } else {
        Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
    }
}

# Test 2: Verify OTP for someone else's transaction
Write-Host "`n[*] Test 2: OTP on Another User's Transaction (IDOR)" -ForegroundColor Yellow

# This would need another user's transaction ID
$OtherUserTxId = "other-user-transaction-" + [guid]::NewGuid()

$Body = @{
    transactionId = $OtherUserTxId
    otpCode = "123456"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/verify-otp" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] VULNERABILITY: Could verify OTP for another user's transaction!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] PASS: Other user's transaction rejected ($StatusCode)" -ForegroundColor Green
}

# Test 3: Complete transaction without OTP
Write-Host "`n[*] Test 3: Skip OTP Verification" -ForegroundColor Yellow

$Body = @{
    transactionId = $FakeTransactionId
    skipVerification = $true
    forceComplete = $true
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/complete" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] CRITICAL: Transaction completed without OTP!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 404 -or $StatusCode -eq 400 -or $StatusCode -eq 405) {
        Write-Host "[+] PASS: Cannot skip OTP verification ($StatusCode)" -ForegroundColor Green
    } else {
        Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
    }
}

# Test 4: Cancel then complete a transaction
Write-Host "`n[*] Test 4: Cancel-Then-Complete Attack" -ForegroundColor Yellow
Write-Host "[*] Attempting to complete a cancelled transaction..." -ForegroundColor Gray

# Try to cancel a fake transaction
$CancelBody = @{
    transactionId = $FakeTransactionId
    reason = "User cancelled"
} | ConvertTo-Json

try {
    $CancelResponse = Invoke-RestMethod -Uri "$BaseUrl/transactions/cancel" `
        -Method POST -Headers $Headers -Body $CancelBody
    Write-Host "    Cancel response received" -ForegroundColor Gray
} catch {
    Write-Host "    Cancel rejected (expected for fake transaction)" -ForegroundColor Gray
}

# Now try to verify OTP on the "cancelled" transaction
try {
    $VerifyBody = @{
        transactionId = $FakeTransactionId
        otpCode = "123456"
    } | ConvertTo-Json
    
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/verify-otp" `
        -Method POST -Headers $Headers -Body $VerifyBody
    
    Write-Host "[!] VULNERABILITY: Cancelled transaction could be completed!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    Write-Host "[+] PASS: Cannot complete cancelled transaction" -ForegroundColor Green
}

# Test 5: Replay completed transaction
Write-Host "`n[*] Test 5: Transaction Replay Attack" -ForegroundColor Yellow
Write-Host "[*] Attempting to re-verify a completed transaction..." -ForegroundColor Gray

$CompletedTxId = "already-completed-" + [guid]::NewGuid()

$Body = @{
    transactionId = $CompletedTxId
    otpCode = "123456"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/verify-otp" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] WARNING: Server may allow re-verification" -ForegroundColor Yellow
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] PASS: Replay rejected ($StatusCode)" -ForegroundColor Green
}

# Test 6: Modify amount after creation
Write-Host "`n[*] Test 6: Amount Modification After Creation" -ForegroundColor Yellow

$ModifyBody = @{
    transactionId = $FakeTransactionId
    amount = 999999
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/modify" `
        -Method PUT -Headers $Headers -Body $ModifyBody
    
    Write-Host "[!] CRITICAL: Transaction amount can be modified!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 404 -or $StatusCode -eq 405) {
        Write-Host "[+] PASS: Amount modification not allowed ($StatusCode)" -ForegroundColor Green
    } else {
        Write-Host "[+] PASS: Modify rejected ($StatusCode)" -ForegroundColor Green
    }
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Transaction state machine appears secure" -ForegroundColor Green
}

Write-Host "`n[*] STATE MACHINE BEST PRACTICES:" -ForegroundColor Yellow
Write-Host "    1. Enforce strict state transitions (PENDING -> PENDING_OTP -> COMPLETED)" -ForegroundColor White
Write-Host "    2. Validate ownership on every state change" -ForegroundColor White
Write-Host "    3. Immutable transaction amounts after creation" -ForegroundColor White
Write-Host "    4. One-time-use OTP codes with expiry" -ForegroundColor White
Write-Host "    5. Idempotency keys to prevent replays" -ForegroundColor White

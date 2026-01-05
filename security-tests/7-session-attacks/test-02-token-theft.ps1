# Session Security Tests
# Test 02: Token Theft Simulation
# Simulates what an attacker can do with a stolen JWT

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SESSION SECURITY TEST 02" -ForegroundColor Cyan
Write-Host "Token Theft Attack Simulation" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Load token (simulating "stolen" token)
if (Test-Path $TokenFile) {
    $StolenToken = Get-Content $TokenFile -Raw
    $StolenToken = $StolenToken.Trim()
} else {
    Write-Host "[-] Token file not found. Run setup-testuser.ps1 first." -ForegroundColor Red
    exit 1
}

$Headers = @{
    "Authorization" = "Bearer $StolenToken"
    "Content-Type" = "application/json"
}

Write-Host "[*] Simulating attacker with stolen JWT token" -ForegroundColor Yellow
Write-Host "[*] Token length: $($StolenToken.Length) characters" -ForegroundColor Gray

$VulnerabilitiesFound = 0

# Test 1: Enumerate user accounts
Write-Host "`n[*] Test 1: Account Enumeration with Stolen Token" -ForegroundColor Yellow

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $Headers
    
    if ($Response.data) {
        $AccountCount = if ($Response.data -is [array]) { $Response.data.Count } else { 1 }
        Write-Host "[!] EXPOSED: Retrieved $AccountCount account(s)" -ForegroundColor Red
        
        foreach ($Account in $Response.data) {
            Write-Host "    - Account: $($Account.accountNumber)" -ForegroundColor Red
            Write-Host "      Balance: $($Account.balance)" -ForegroundColor Red
            Write-Host "      Status: $($Account.status)" -ForegroundColor Red
        }
        $VulnerabilitiesFound++
    }
} catch {
    Write-Host "[+] Account access blocked" -ForegroundColor Green
}

# Test 2: View transaction history
Write-Host "`n[*] Test 2: Transaction History Exfiltration" -ForegroundColor Yellow

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/history" -Method GET -Headers $Headers
    
    if ($Response.data) {
        Write-Host "[!] EXPOSED: Transaction history accessible" -ForegroundColor Red
        $VulnerabilitiesFound++
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 404) {
        Write-Host "[?] Endpoint not found (may use different path)" -ForegroundColor Yellow
    } else {
        Write-Host "[+] Transaction history blocked ($StatusCode)" -ForegroundColor Green
    }
}

# Test 3: Retrieve sensitive user data
Write-Host "`n[*] Test 3: PII Extraction" -ForegroundColor Yellow

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/users/me" -Method GET -Headers $Headers
    
    if ($Response.data) {
        Write-Host "[!] EXPOSED: User PII accessible" -ForegroundColor Red
        Write-Host "    - Email: $($Response.data.email)" -ForegroundColor Red
        Write-Host "    - Name: $($Response.data.name)" -ForegroundColor Red
        $VulnerabilitiesFound++
    }
} catch {
    Write-Host "[+] User data protected" -ForegroundColor Green
}

# Test 4: Attempt password change
Write-Host "`n[*] Test 4: Password Change Attempt" -ForegroundColor Yellow

$PasswordBody = @{
    currentPassword = "wrongpassword"
    newPassword = "hacked123!"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/users/change-password" `
        -Method POST -Headers $Headers -Body $PasswordBody
    
    Write-Host "[!] CRITICAL: Password change endpoint accessible!" -ForegroundColor Red
    $VulnerabilitiesFound++
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 400 -or $StatusCode -eq 401) {
        Write-Host "[+] Password change requires current password verification" -ForegroundColor Green
    } elseif ($StatusCode -eq 404) {
        Write-Host "[?] Endpoint not found" -ForegroundColor Yellow
    } else {
        Write-Host "[+] Password change blocked ($StatusCode)" -ForegroundColor Green
    }
}

# Test 5: Initiate transfer (most dangerous)
Write-Host "`n[*] Test 5: Unauthorized Transfer Attempt" -ForegroundColor Yellow

$TransferBody = @{
    senderAccountId = "unknown"
    receiverAccountNumber = "9999999999"
    amount = 1
    transactionType = "INTERNAL_TRANSFER"
    description = "test transfer"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
        -Method POST -Headers $Headers -Body $TransferBody
    
    if ($Response.code -eq 1000) {
        Write-Host "[!] CRITICAL: Transfer initiated with stolen token!" -ForegroundColor Red
        Write-Host "    Transaction ID: $($Response.data.transactionId)" -ForegroundColor Red
        $VulnerabilitiesFound++
    } else {
        Write-Host "[+] Transfer blocked: $($Response.message)" -ForegroundColor Green
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] Transfer blocked (HTTP $StatusCode)" -ForegroundColor Green
}

# Test 6: Register new device (persistence attack)
Write-Host "`n[*] Test 6: Device Registration (Persistence Attack)" -ForegroundColor Yellow

$DeviceBody = @{
    deviceName = "Attacker Device"
    deviceType = "ANDROID"
    publicKey = "-----BEGIN PUBLIC KEY-----`nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...(fake)...`n-----END PUBLIC KEY-----"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
        -Method POST -Headers $Headers -Body $DeviceBody
    
    if ($Response.code -eq 1000) {
        Write-Host "[!] CRITICAL: Attacker device registered!" -ForegroundColor Red
        Write-Host "    This allows persistent access via Smart OTP" -ForegroundColor Red
        $VulnerabilitiesFound++
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 400) {
        Write-Host "[+] Device registration requires valid key format" -ForegroundColor Green
    } else {
        Write-Host "[+] Device registration blocked ($StatusCode)" -ForegroundColor Green
    }
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "ATTACK SIMULATION COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
    Write-Host "[*] With a stolen token, attacker can:" -ForegroundColor Red
    Write-Host "    - View account balances and transaction history" -ForegroundColor Red
    Write-Host "    - Extract personal information" -ForegroundColor Red
    Write-Host "    - Potentially initiate transfers" -ForegroundColor Red
    Write-Host "    - Register persistent device access" -ForegroundColor Red
} else {
    Write-Host "`n[+] Token theft impact is limited" -ForegroundColor Green
}

Write-Host "`n[*] MITIGATIONS:" -ForegroundColor Yellow
Write-Host "    1. Short token expiry (15 min for access tokens)" -ForegroundColor White
Write-Host "    2. Require re-authentication for sensitive actions" -ForegroundColor White
Write-Host "    3. Device binding - tokens only valid from registered devices" -ForegroundColor White
Write-Host "    4. Transaction signing - require Smart OTP for all transfers" -ForegroundColor White
Write-Host "    5. Anomaly detection - flag unusual access patterns" -ForegroundColor White

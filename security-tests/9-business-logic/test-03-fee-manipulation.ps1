# Business Logic Attacks
# Test 03: Fee Manipulation Attack
# Attempts to manipulate transaction fees or bypass them entirely

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "BUSINESS LOGIC TEST 03" -ForegroundColor Cyan
Write-Host "Fee Manipulation Attack" -ForegroundColor Cyan
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

Write-Host "[*] Testing for fee calculation vulnerabilities..." -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Get account first
Write-Host "`n[*] Getting account information..." -ForegroundColor Yellow

try {
    $AccountsResponse = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $Headers
    $SourceAccount = $AccountsResponse.data[0]
    $AccountId = $SourceAccount.accountId
    $AccountNumber = $SourceAccount.accountNumber
    Write-Host "[+] Account: $AccountNumber" -ForegroundColor Green
} catch {
    Write-Host "[-] Failed to get accounts" -ForegroundColor Red
    exit 1
}

# Test 1: Submit negative fee
Write-Host "`n[*] Test 1: Negative Fee Injection" -ForegroundColor Yellow

$Body = @{
    senderAccountId = $AccountId
    senderAccountNumber = $AccountNumber
    receiverAccountNumber = "9999999999"
    amount = 100
    transactionType = "EXTERNAL_TRANSFER"
    description = "Test"
    feeAmount = -50  # Negative fee = get money?
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
        -Method POST -Headers $Headers -Body $Body
    
    if ($Response.data.feeAmount -lt 0) {
        Write-Host "[!] CRITICAL: Negative fee accepted!" -ForegroundColor Red
        Write-Host "    Fee charged: $($Response.data.feeAmount)" -ForegroundColor Red
        $VulnerabilitiesFound++
    } else {
        Write-Host "[+] PASS: Server calculated fee correctly (ignored input)" -ForegroundColor Green
        Write-Host "    Server fee: $($Response.data.feeAmount)" -ForegroundColor Gray
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[+] PASS: Request rejected ($StatusCode)" -ForegroundColor Green
}

# Test 2: Submit zero fee for fee-required transaction
Write-Host "`n[*] Test 2: Zero Fee for External Transfer" -ForegroundColor Yellow

$Body = @{
    senderAccountId = $AccountId
    senderAccountNumber = $AccountNumber
    receiverAccountNumber = "9999999999"
    amount = 1000
    transactionType = "EXTERNAL_TRANSFER"
    description = "Test"
    feeAmount = 0
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
        -Method POST -Headers $Headers -Body $Body
    
    if ($Response.data.feeAmount -eq 0) {
        Write-Host "[!] WARNING: Zero fee on external transfer accepted" -ForegroundColor Yellow
        Write-Host "    May be intentional (fee-free period)" -ForegroundColor Yellow
    } else {
        Write-Host "[+] PASS: Server applied correct fee: $($Response.data.feeAmount)" -ForegroundColor Green
    }
} catch {
    Write-Host "[+] Transfer rejected (may be invalid recipient)" -ForegroundColor Green
}

# Test 3: Precision/rounding exploit
Write-Host "`n[*] Test 3: Precision/Rounding Exploit" -ForegroundColor Yellow
Write-Host "[*] Testing if fractional amounts cause rounding errors..." -ForegroundColor Gray

$TestAmounts = @(
    0.001,
    0.009,
    0.0099,
    0.00001,
    1.99999999999
)

foreach ($Amount in $TestAmounts) {
    $Body = @{
        senderAccountId = $AccountId
        senderAccountNumber = $AccountNumber
        receiverAccountNumber = "9999999999"
        amount = $Amount
        transactionType = "INTERNAL_TRANSFER"
        description = "Rounding test"
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
            -Method POST -Headers $Headers -Body $Body
        
        Write-Host "    Amount $Amount -> Processed as $($Response.data.amount)" -ForegroundColor Yellow
        
        if ($Response.data.amount -ne $Amount) {
            Write-Host "    [!] Rounding occurred: $Amount -> $($Response.data.amount)" -ForegroundColor Yellow
        }
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -eq 400) {
            Write-Host "    Amount $Amount -> Rejected (min amount check)" -ForegroundColor Green
        }
    }
}

# Test 4: Integer overflow on amount
Write-Host "`n[*] Test 4: Integer Overflow Attack" -ForegroundColor Yellow

$OverflowAmounts = @(
    9999999999999999,
    99999999999999999999,
    [long]::MaxValue
)

foreach ($Amount in $OverflowAmounts) {
    $Body = @{
        senderAccountId = $AccountId
        senderAccountNumber = $AccountNumber
        receiverAccountNumber = "9999999999"
        amount = $Amount
        transactionType = "INTERNAL_TRANSFER"
        description = "Overflow test"
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
            -Method POST -Headers $Headers -Body $Body
        
        Write-Host "[!] CRITICAL: Huge amount accepted: $Amount" -ForegroundColor Red
        $VulnerabilitiesFound++
    } catch {
        Write-Host "[+] Amount $Amount rejected" -ForegroundColor Green
    }
}

# Test 5: Currency manipulation (if multi-currency)
Write-Host "`n[*] Test 5: Currency Mismatch" -ForegroundColor Yellow

$Body = @{
    senderAccountId = $AccountId
    senderAccountNumber = $AccountNumber
    receiverAccountNumber = "9999999999"
    amount = 100
    currency = "BTC"  # Try to use different currency
    transactionType = "INTERNAL_TRANSFER"
    description = "Currency test"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[?] Check if currency was respected or ignored" -ForegroundColor Yellow
} catch {
    Write-Host "[+] Currency parameter rejected/ignored (good)" -ForegroundColor Green
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Fee handling appears secure" -ForegroundColor Green
}

Write-Host "`n[*] FEE SECURITY BEST PRACTICES:" -ForegroundColor Yellow
Write-Host "    1. NEVER trust client-submitted fee values" -ForegroundColor White
Write-Host "    2. Calculate fees server-side based on rules" -ForegroundColor White
Write-Host "    3. Use BigDecimal for monetary calculations" -ForegroundColor White
Write-Host "    4. Define minimum transaction amounts" -ForegroundColor White
Write-Host "    5. Validate against max transaction limits" -ForegroundColor White
Write-Host "    6. Round consistently (HALF_UP recommended)" -ForegroundColor White

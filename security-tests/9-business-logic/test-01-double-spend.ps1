# Business Logic Attacks
# Test 01: Race Condition Double-Spend
# Attempts to spend the same balance twice via concurrent requests

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "BUSINESS LOGIC TEST 01" -ForegroundColor Cyan
Write-Host "Race Condition Double-Spend Attack" -ForegroundColor Cyan
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

Write-Host "[*] This test attempts to exploit race conditions in balance updates" -ForegroundColor Yellow
Write-Host "[*] A double-spend attack sends multiple transfers simultaneously" -ForegroundColor Yellow
Write-Host "[*] hoping they all check balance BEFORE any deducts it" -ForegroundColor Yellow

# Step 1: Get current account info
Write-Host "`n[*] Step 1: Getting account information..." -ForegroundColor Yellow

try {
    $AccountsResponse = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $Headers
    
    if ($AccountsResponse.data -and $AccountsResponse.data.Count -gt 0) {
        $SourceAccount = $AccountsResponse.data[0]
        $AccountId = $SourceAccount.accountId
        $AccountNumber = $SourceAccount.accountNumber
        $InitialBalance = $SourceAccount.balance
        
        Write-Host "[+] Source Account: $AccountNumber" -ForegroundColor Green
        Write-Host "[+] Initial Balance: `$$InitialBalance" -ForegroundColor Green
    } else {
        Write-Host "[-] No accounts found" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "[-] Failed to get accounts: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Prepare concurrent transfer requests
Write-Host "`n[*] Step 2: Preparing concurrent transfers..." -ForegroundColor Yellow

# Calculate: If balance is $1000, try to send 3x$400 = $1200 (exceeds balance)
$TransferAmount = [math]::Floor($InitialBalance * 0.4)  # 40% of balance
$ConcurrentRequests = 3

if ($TransferAmount -lt 10) {
    Write-Host "[-] Balance too low for test (need at least `$25)" -ForegroundColor Red
    exit 1
}

Write-Host "[*] Will attempt $ConcurrentRequests concurrent transfers of `$$TransferAmount" -ForegroundColor Gray
Write-Host "[*] Total attempted: `$$($TransferAmount * $ConcurrentRequests) (vs balance: `$$InitialBalance)" -ForegroundColor Gray

$TransferBody = @{
    senderAccountId = $AccountId
    senderAccountNumber = $AccountNumber
    receiverAccountNumber = "9999999999"  # Fake recipient
    amount = $TransferAmount
    transactionType = "INTERNAL_TRANSFER"
    description = "Race condition test"
} | ConvertTo-Json

# Step 3: Fire concurrent requests
Write-Host "`n[*] Step 3: Firing $ConcurrentRequests concurrent requests..." -ForegroundColor Yellow

$Jobs = @()

for ($i = 1; $i -le $ConcurrentRequests; $i++) {
    $ScriptBlock = {
        param($Url, $Token, $Body, $RequestNum)
        
        $Headers = @{
            "Authorization" = "Bearer $Token"
            "Content-Type" = "application/json"
        }
        
        try {
            $Response = Invoke-RestMethod -Uri "$Url/transactions/transfers" `
                -Method POST -Headers $Headers -Body $Body
            
            return @{
                RequestNum = $RequestNum
                Success = $true
                TransactionId = $Response.data.transactionId
                Status = $Response.data.status
            }
        } catch {
            return @{
                RequestNum = $RequestNum
                Success = $false
                Error = $_.Exception.Message
                StatusCode = $_.Exception.Response.StatusCode.value__
            }
        }
    }
    
    $Jobs += Start-Job -ScriptBlock $ScriptBlock -ArgumentList $BaseUrl, $Token, $TransferBody, $i
}

# Wait for all jobs to complete
Write-Host "[*] Waiting for all requests to complete..." -ForegroundColor Gray
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

# Step 4: Analyze results
Write-Host "`n[*] Step 4: Analyzing results..." -ForegroundColor Yellow

$SuccessCount = 0
$FailCount = 0

foreach ($Result in $Results) {
    if ($Result.Success) {
        $SuccessCount++
        Write-Host "    [+] Request $($Result.RequestNum): SUCCESS (TxId: $($Result.TransactionId))" -ForegroundColor Green
    } else {
        $FailCount++
        Write-Host "    [-] Request $($Result.RequestNum): FAILED ($($Result.StatusCode) - $($Result.Error))" -ForegroundColor Red
    }
}

# Step 5: Check final balance
Write-Host "`n[*] Step 5: Checking final balance..." -ForegroundColor Yellow

Start-Sleep -Seconds 2  # Wait for transactions to process

try {
    $FinalAccountsResponse = Invoke-RestMethod -Uri "$BaseUrl/accounts" -Method GET -Headers $Headers
    $FinalAccount = $FinalAccountsResponse.data | Where-Object { $_.accountId -eq $AccountId }
    $FinalBalance = $FinalAccount.balance
    
    Write-Host "[*] Initial Balance: `$$InitialBalance" -ForegroundColor White
    Write-Host "[*] Final Balance: `$$FinalBalance" -ForegroundColor White
    Write-Host "[*] Actual Deducted: `$$($InitialBalance - $FinalBalance)" -ForegroundColor White
    Write-Host "[*] Expected if all succeeded: `$$($TransferAmount * $SuccessCount)" -ForegroundColor White
} catch {
    Write-Host "[-] Could not verify final balance" -ForegroundColor Red
}

# Verdict
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "VERDICT" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$ExpectedSuccesses = [math]::Floor($InitialBalance / $TransferAmount)

if ($SuccessCount -gt $ExpectedSuccesses) {
    Write-Host "`n[!] CRITICAL VULNERABILITY: DOUBLE-SPEND DETECTED!" -ForegroundColor Red
    Write-Host "    $SuccessCount transfers succeeded (max should be $ExpectedSuccesses)" -ForegroundColor Red
    Write-Host "    Race condition allows spending more than balance" -ForegroundColor Red
    Write-Host "`n    FIX: Use optimistic locking (@Version) or database-level locks" -ForegroundColor Yellow
} elseif ($SuccessCount -gt 1 -and ($SuccessCount * $TransferAmount) -gt $InitialBalance) {
    Write-Host "`n[!] WARNING: Multiple transfers succeeded" -ForegroundColor Yellow
    Write-Host "    Verify database shows correct balance" -ForegroundColor Yellow
} else {
    Write-Host "`n[+] PASS: Race condition appears to be handled" -ForegroundColor Green
    Write-Host "    Only $SuccessCount of $ConcurrentRequests succeeded" -ForegroundColor Green
    Write-Host "    System likely uses proper locking" -ForegroundColor Green
}

Write-Host "`n[*] MITIGATIONS:" -ForegroundColor Yellow
Write-Host "    1. Use @Version (optimistic locking) on Account entity" -ForegroundColor White
Write-Host "    2. Use SELECT FOR UPDATE in critical queries" -ForegroundColor White
Write-Host "    3. Implement idempotency keys" -ForegroundColor White
Write-Host "    4. Use database transactions with SERIALIZABLE isolation" -ForegroundColor White

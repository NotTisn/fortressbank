# Cryptographic Attacks
# Test 03: Challenge Randomness Analysis
# Tests if OTP challenges and tokens have sufficient entropy

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "CRYPTO ATTACK TEST 03" -ForegroundColor Cyan
Write-Host "Challenge Randomness Analysis" -ForegroundColor Cyan
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

Write-Host "[*] Analyzing randomness of generated challenges and tokens..." -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Collect multiple challenge IDs
Write-Host "`n[*] Test 1: Challenge ID Entropy Analysis" -ForegroundColor Yellow
Write-Host "[*] Collecting challenge samples..." -ForegroundColor Gray

$ChallengeIds = @()
$OTPCodes = @()
$TransactionIds = @()

$Body = @{
    senderAccountId = "test-account-entropy"
    receiverAccountNumber = "9999999999"
    amount = 50
    transactionType = "INTERNAL_TRANSFER"
    description = "Entropy test"
} | ConvertTo-Json

for ($i = 0; $i -lt 10; $i++) {
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers/initiate" `
            -Method POST -Headers $Headers -Body $Body
        
        if ($Response.data.challengeId) {
            $ChallengeIds += $Response.data.challengeId
        }
        if ($Response.data.transactionId) {
            $TransactionIds += $Response.data.transactionId
        }
    } catch {
        # Expected - may hit rate limits or validation
    }
    Start-Sleep -Milliseconds 100
}

Write-Host "    Collected $($ChallengeIds.Count) challenge IDs" -ForegroundColor Gray
Write-Host "    Collected $($TransactionIds.Count) transaction IDs" -ForegroundColor Gray

# Analyze challenge ID patterns
if ($ChallengeIds.Count -ge 3) {
    Write-Host "`n[*] Analyzing challenge ID patterns..." -ForegroundColor Yellow
    
    # Check for sequential patterns
    $Numeric = $ChallengeIds | Where-Object { $_ -match '^\d+$' }
    if ($Numeric.Count -gt 0) {
        $Sorted = $Numeric | Sort-Object { [long]$_ }
        $Differences = @()
        for ($i = 1; $i -lt $Sorted.Count; $i++) {
            $Differences += [long]$Sorted[$i] - [long]$Sorted[$i-1]
        }
        
        $AvgDiff = ($Differences | Measure-Object -Average).Average
        if ($AvgDiff -lt 10) {
            Write-Host "[!] CRITICAL: Challenge IDs appear sequential!" -ForegroundColor Red
            Write-Host "    Average increment: $AvgDiff" -ForegroundColor Red
            Write-Host "    Attackers can predict future challenges" -ForegroundColor Red
            $VulnerabilitiesFound++
        }
    }
    
    # Check for common prefixes (timestamp-based)
    $Prefixes = $ChallengeIds | ForEach-Object { $_.Substring(0, [Math]::Min(8, $_.Length)) }
    $UniquePrefixes = $Prefixes | Select-Object -Unique
    
    if ($UniquePrefixes.Count -eq 1) {
        Write-Host "[!] WARNING: All challenges share same prefix" -ForegroundColor Yellow
        Write-Host "    Prefix: $($UniquePrefixes[0])" -ForegroundColor Yellow
        Write-Host "    May indicate timestamp-based generation" -ForegroundColor Yellow
    }
    
    # Check string length consistency (UUIDs are 36 chars)
    $Lengths = $ChallengeIds | ForEach-Object { $_.Length } | Select-Object -Unique
    if ($Lengths -contains 36) {
        Write-Host "[+] Challenge IDs appear to be UUIDs (good entropy)" -ForegroundColor Green
    } else {
        Write-Host "[?] Non-UUID format detected, verify entropy manually" -ForegroundColor Yellow
        Write-Host "    Lengths: $($Lengths -join ', ')" -ForegroundColor Gray
    }
    
    # Character distribution analysis
    $AllChars = ($ChallengeIds -join '').ToCharArray()
    $CharCounts = $AllChars | Group-Object | Sort-Object -Property Count -Descending
    $MaxChar = $CharCounts[0]
    $TotalChars = $AllChars.Count
    $ExpectedFreq = $TotalChars / $CharCounts.Count
    
    if ($MaxChar.Count -gt ($ExpectedFreq * 2)) {
        Write-Host "[?] Uneven character distribution detected" -ForegroundColor Yellow
        Write-Host "    Most frequent: '$($MaxChar.Name)' appears $($MaxChar.Count) times" -ForegroundColor Gray
    } else {
        Write-Host "[+] PASS: Character distribution appears uniform" -ForegroundColor Green
    }
} else {
    Write-Host "[?] Insufficient samples for analysis" -ForegroundColor Yellow
}

# Test 2: Timestamp-based prediction
Write-Host "`n[*] Test 2: Timestamp Prediction Attack" -ForegroundColor Yellow
Write-Host "[*] Checking if challenges are predictable from timestamp..." -ForegroundColor Gray

# Get current timestamp and try to predict challenge format
$EpochMs = [long]([DateTimeOffset]::Now.ToUnixTimeMilliseconds())
$EpochSec = [long]([DateTimeOffset]::Now.ToUnixTimeSeconds())

$TimestampFormats = @(
    $EpochMs.ToString(),
    $EpochSec.ToString(),
    [Convert]::ToString($EpochMs, 16),
    [Convert]::ToString($EpochSec, 16)
)

$MatchFound = $false
foreach ($ChallengeId in $ChallengeIds) {
    foreach ($Format in $TimestampFormats) {
        if ($ChallengeId.Contains($Format.Substring(0, [Math]::Min(8, $Format.Length)))) {
            Write-Host "[!] WARNING: Challenge contains timestamp component!" -ForegroundColor Yellow
            Write-Host "    Challenge: $ChallengeId" -ForegroundColor Yellow
            Write-Host "    Timestamp: $Format" -ForegroundColor Yellow
            $MatchFound = $true
            break
        }
    }
    if ($MatchFound) { break }
}

if (-not $MatchFound) {
    Write-Host "[+] PASS: No obvious timestamp patterns detected" -ForegroundColor Green
}

# Test 3: Collision detection
Write-Host "`n[*] Test 3: Challenge Collision Check" -ForegroundColor Yellow

$UniqueIds = $ChallengeIds | Select-Object -Unique
$Collisions = $ChallengeIds.Count - $UniqueIds.Count

if ($Collisions -gt 0) {
    Write-Host "[!] CRITICAL: $Collisions collision(s) detected!" -ForegroundColor Red
    Write-Host "    Indicates insufficient randomness" -ForegroundColor Red
    $VulnerabilitiesFound++
} else {
    Write-Host "[+] PASS: No collisions in $($ChallengeIds.Count) samples" -ForegroundColor Green
}

# Test 4: OTP Code Entropy (if SMS OTP is used)
Write-Host "`n[*] Test 4: OTP Code Brute-Force Feasibility" -ForegroundColor Yellow

# Try to get OTP challenge
$SMSBody = @{
    senderAccountId = "test-sms-account"
    receiverAccountNumber = "external-bank-9999"
    amount = 50000  # High amount to trigger SMS OTP
    transactionType = "EXTERNAL_TRANSFER"
    description = "OTP entropy test"
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers/initiate" `
        -Method POST -Headers $Headers -Body $SMSBody
    
    if ($Response.data.challengeType -eq "SMS_OTP") {
        Write-Host "[*] SMS OTP challenge triggered" -ForegroundColor Gray
        
        # Test brute force feasibility
        Write-Host "[*] Testing OTP brute-force protection..." -ForegroundColor Gray
        
        $AttemptCount = 0
        $Blocked = $false
        
        for ($i = 0; $i -lt 20; $i++) {
            $RandomOTP = Get-Random -Minimum 100000 -Maximum 999999
            $VerifyBody = @{
                transactionId = $Response.data.transactionId
                otpCode = $RandomOTP.ToString()
            } | ConvertTo-Json
            
            try {
                Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers/verify-otp" `
                    -Method POST -Headers $Headers -Body $VerifyBody
            } catch {
                $StatusCode = $_.Exception.Response.StatusCode.value__
                if ($StatusCode -eq 429) {
                    Write-Host "[+] PASS: Rate limited after $AttemptCount attempts" -ForegroundColor Green
                    $Blocked = $true
                    break
                }
            }
            $AttemptCount++
        }
        
        if (-not $Blocked) {
            Write-Host "[!] WARNING: No rate limiting on OTP verification" -ForegroundColor Yellow
            Write-Host "    6-digit OTP brute-forceable in ~1 million attempts" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "[?] Could not test OTP brute-force (transfer initiation failed)" -ForegroundColor Gray
}

# Test 5: Session Token Entropy
Write-Host "`n[*] Test 5: JWT Token ID Uniqueness" -ForegroundColor Yellow

# Extract JTI from multiple tokens
$TokenParts = $Token.Split('.')
if ($TokenParts.Count -eq 3) {
    $PayloadPadded = $TokenParts[1].Replace('-','+').Replace('_','/')
    while ($PayloadPadded.Length % 4 -ne 0) { $PayloadPadded += '=' }
    
    try {
        $Payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($PayloadPadded))
        $PayloadJson = $Payload | ConvertFrom-Json
        
        if ($PayloadJson.jti) {
            $JTI = $PayloadJson.jti
            Write-Host "    Token JTI: $JTI" -ForegroundColor Gray
            
            if ($JTI.Length -ge 32) {
                Write-Host "[+] PASS: JTI has sufficient length ($($JTI.Length) chars)" -ForegroundColor Green
            } else {
                Write-Host "[!] WARNING: Short JTI ($($JTI.Length) chars)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "[?] No JTI claim in token" -ForegroundColor Gray
        }
    } catch {
        Write-Host "[?] Could not parse token payload" -ForegroundColor Gray
    }
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Randomness analysis complete" -ForegroundColor Green
}

Write-Host "`n[*] ENTROPY REQUIREMENTS (NIST SP 800-90A/B):" -ForegroundColor Yellow
Write-Host "    1. Use cryptographically secure RNG (SecureRandom)" -ForegroundColor White
Write-Host "    2. Minimum 128 bits of entropy for security tokens" -ForegroundColor White
Write-Host "    3. UUID v4 recommended for challenge IDs" -ForegroundColor White
Write-Host "    4. No sequential or timestamp-predictable patterns" -ForegroundColor White
Write-Host "    5. OTP codes should have retry limits (max 3-5 attempts)" -ForegroundColor White
Write-Host "    6. Implement exponential backoff on failures" -ForegroundColor White

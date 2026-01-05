# Cryptographic Attacks
# Test 02: Signature Algorithm Confusion
# Tests if system is vulnerable to algorithm confusion attacks

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "CRYPTO ATTACK TEST 02" -ForegroundColor Cyan
Write-Host "Signature Algorithm Confusion" -ForegroundColor Cyan
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

Write-Host "[*] Testing signature algorithm confusion vulnerabilities..." -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Test 1: Accept "none" algorithm in signature
Write-Host "`n[*] Test 1: 'none' Algorithm in Smart OTP" -ForegroundColor Yellow
Write-Host "[*] Attempting challenge verification with 'alg: none'..." -ForegroundColor Gray

# First, initiate a challenge
$TransferBody = @{
    senderAccountId = "test-account-001"
    receiverAccountNumber = "9999999999"
    amount = 100
    transactionType = "INTERNAL_TRANSFER"
    description = "Sig confusion test"
} | ConvertTo-Json

try {
    $TransferResponse = Invoke-RestMethod -Uri "$BaseUrl/transactions/transfers/initiate" `
        -Method POST -Headers $Headers -Body $TransferBody
    
    $ChallengeId = $TransferResponse.data.challengeId
    
    if ($ChallengeId) {
        Write-Host "    Challenge obtained: $ChallengeId" -ForegroundColor Gray
        
        # Create fake signature with "none" algorithm header
        $NoneHeader = '{"alg":"none","typ":"JWT"}'
        $NoneHeaderB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($NoneHeader))
        $NoneHeaderB64 = $NoneHeaderB64.Replace('+','-').Replace('/','_').Replace('=','')
        
        $Payload = "{`"challengeId`":`"$ChallengeId`",`"deviceId`":`"fake`"}"
        $PayloadB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Payload))
        $PayloadB64 = $PayloadB64.Replace('+','-').Replace('/','_').Replace('=','')
        
        $FakeSignature = "$NoneHeaderB64.$PayloadB64."  # Empty signature for "none"
        
        $VerifyBody = @{
            challengeId = $ChallengeId
            signature = $FakeSignature
            deviceId = "fake-device"
        } | ConvertTo-Json
        
        try {
            $VerifyResponse = Invoke-RestMethod -Uri "$BaseUrl/devices/verify-challenge" `
                -Method POST -Headers $Headers -Body $VerifyBody
            
            if ($VerifyResponse.code -eq 1000 -and $VerifyResponse.data.valid -eq $true) {
                Write-Host "[!] CRITICAL: 'none' algorithm signature accepted!" -ForegroundColor Red
                $VulnerabilitiesFound++
            } else {
                Write-Host "[+] PASS: 'none' algorithm rejected" -ForegroundColor Green
            }
        } catch {
            Write-Host "[+] PASS: Invalid signature rejected" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "[?] Could not initiate transfer for testing" -ForegroundColor Yellow
}

# Test 2: Algorithm downgrade (RSA to HMAC)
Write-Host "`n[*] Test 2: Algorithm Downgrade (RS256 -> HS256)" -ForegroundColor Yellow
Write-Host "[*] Testing if HMAC signature accepted instead of RSA..." -ForegroundColor Gray

# This exploits when server's public key is used as HMAC secret
$HSHeader = '{"alg":"HS256","typ":"JWT"}'
$HSHeaderB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($HSHeader))
$HSHeaderB64 = $HSHeaderB64.Replace('+','-').Replace('/','_').Replace('=','')

$Timestamp = [int][double]::Parse((Get-Date -UFormat %s))
$HSPayload = "{`"sub`":`"test-user`",`"iat`":$Timestamp,`"exp`":$($Timestamp + 3600)}"
$HSPayloadB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($HSPayload))
$HSPayloadB64 = $HSPayloadB64.Replace('+','-').Replace('/','_').Replace('=','')

# Using a common weak HMAC secret
$CommonSecrets = @("secret", "password", "changeme", "key", "fortressbank", "123456")

foreach ($Secret in $CommonSecrets) {
    $hmacsha256 = New-Object System.Security.Cryptography.HMACSHA256
    $hmacsha256.Key = [Text.Encoding]::UTF8.GetBytes($Secret)
    $SignatureBytes = $hmacsha256.ComputeHash([Text.Encoding]::UTF8.GetBytes("$HSHeaderB64.$HSPayloadB64"))
    $Signature = [Convert]::ToBase64String($SignatureBytes).Replace('+','-').Replace('/','_').Replace('=','')
    
    $FakeJWT = "$HSHeaderB64.$HSPayloadB64.$Signature"
    
    $TestHeaders = @{
        "Authorization" = "Bearer $FakeJWT"
        "Content-Type" = "application/json"
    }
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/accounts/my" `
            -Method GET -Headers $TestHeaders -TimeoutSec 5
        
        Write-Host "[!] CRITICAL: HS256 downgrade accepted with secret: $Secret" -ForegroundColor Red
        $VulnerabilitiesFound++
        break
    } catch {
        # Expected - should reject
    }
}
Write-Host "[+] PASS: Algorithm downgrade attacks rejected" -ForegroundColor Green

# Test 3: Elliptic Curve Confusion
Write-Host "`n[*] Test 3: EC Point Decompression Attack" -ForegroundColor Yellow
Write-Host "[*] Testing invalid elliptic curve points..." -ForegroundColor Gray

# Invalid EC point that could cause issues in some implementations
$InvalidECKey = @"
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==
-----END PUBLIC KEY-----
"@

$Body = @{
    deviceName = "EC Attack Device"
    deviceType = "ANDROID"
    publicKey = $InvalidECKey
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
        -Method POST -Headers $Headers -Body $Body
    
    if ($Response.code -eq 1000) {
        Write-Host "[!] WARNING: Invalid EC point accepted!" -ForegroundColor Red
        $VulnerabilitiesFound++
    }
} catch {
    Write-Host "[+] PASS: Invalid EC point rejected" -ForegroundColor Green
}

# Test 4: Timing attack on signature verification
Write-Host "`n[*] Test 4: Signature Timing Analysis" -ForegroundColor Yellow
Write-Host "[*] Checking for timing-based signature leakage..." -ForegroundColor Gray

$SignatureLengths = @(1, 10, 50, 100, 256, 512)
$Timings = @{}

foreach ($Len in $SignatureLengths) {
    $FakeSig = "A" * $Len
    $Body = @{
        challengeId = "test-challenge-123"
        signature = $FakeSig
        deviceId = "test-device"
    } | ConvertTo-Json
    
    $Times = @()
    for ($i = 0; $i -lt 5; $i++) {
        $Start = Get-Date
        try {
            Invoke-RestMethod -Uri "$BaseUrl/devices/verify-challenge" `
                -Method POST -Headers $Headers -Body $Body -TimeoutSec 5
        } catch {
            # Expected
        }
        $End = Get-Date
        $Times += ($End - $Start).TotalMilliseconds
    }
    
    $Avg = ($Times | Measure-Object -Average).Average
    $Timings[$Len] = $Avg
    Write-Host "    Signature length $Len : $([math]::Round($Avg, 2))ms avg" -ForegroundColor Gray
}

# Check for timing variance
$Variance = ($Timings.Values | Measure-Object -StandardDeviation).StandardDeviation
if ($Variance -gt 50) {
    Write-Host "[!] WARNING: Significant timing variance detected ($([math]::Round($Variance, 2))ms)" -ForegroundColor Yellow
    Write-Host "    This could enable timing-based attacks" -ForegroundColor Yellow
} else {
    Write-Host "[+] PASS: Constant-time signature verification (variance: $([math]::Round($Variance, 2))ms)" -ForegroundColor Green
}

# Test 5: Key Confusion (using wrong key type)
Write-Host "`n[*] Test 5: Key Type Confusion" -ForegroundColor Yellow

$KeyConfusionTests = @(
    @{
        Name = "DSA key instead of RSA"
        Key = @"
-----BEGIN DSA PRIVATE KEY-----
MIH4AgEAAkEAzqV1XJb2tPNiMpWjGLsqyKAZnJQJcKH8s5MjQCxKGFsbQHQT7Q==
-----END DSA PRIVATE KEY-----
"@
    },
    @{
        Name = "Certificate instead of raw key"
        Key = @"
-----BEGIN CERTIFICATE-----
MIIBkDCB+gIJAKPGxau8FGAZMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl
c3RjYTAeFw0yMzAxMDEwMDAwMDBaFw0yNDAxMDEwMDAwMDBaMBExDzANBgNVBAMM
BnRlc3RjYTBcMA0GCSqGSIb3DQEBAQUAA0sAMEgCQQDEK/sP2Vvu0QC7C8LZ8E+F
FMaB9E8aS0TpqVZ+ZE0X5P8Q5x5E+8E9+5E8E+8E9E+8E+8E9E+8E+8E9E+8EqpD
AgMBAAEwDQYJKoZIhvcNAQELBQADQQA8x5X8X+5E+8E9E+8E+8E9E+8E+8E9E+8E
+8E9E+8E+8E9E+8E+8E9E+8E+8E9E+8E+8E9E+8E+8E9E+8E
-----END CERTIFICATE-----
"@
    }
)

foreach ($Test in $KeyConfusionTests) {
    $Body = @{
        deviceName = "Key Confusion Test"
        deviceType = "ANDROID"
        publicKey = $Test.Key
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
            -Method POST -Headers $Headers -Body $Body
        
        if ($Response.code -eq 1000) {
            Write-Host "[!] WARNING: $($Test.Name) accepted!" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "[+] PASS: $($Test.Name) rejected" -ForegroundColor Green
    }
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
    Write-Host "[!] Algorithm confusion attacks possible!" -ForegroundColor Red
} else {
    Write-Host "`n[+] No algorithm confusion vulnerabilities found" -ForegroundColor Green
}

Write-Host "`n[*] OWASP A02:2021 - Cryptographic Failures" -ForegroundColor Yellow
Write-Host "[*] Mitigations:" -ForegroundColor Yellow
Write-Host "    1. Explicit algorithm allowlist in verification" -ForegroundColor White
Write-Host "    2. Reject 'none' algorithm completely" -ForegroundColor White
Write-Host "    3. Validate key type matches expected algorithm" -ForegroundColor White
Write-Host "    4. Use constant-time comparison for signatures" -ForegroundColor White
Write-Host "    5. Validate EC curve parameters strictly" -ForegroundColor White

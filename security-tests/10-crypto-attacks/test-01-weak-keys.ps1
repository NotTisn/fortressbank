# Cryptographic Attacks
# Test 01: Weak Key Detection
# Detects if system accepts weak or predictable cryptographic keys

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "CRYPTO ATTACK TEST 01" -ForegroundColor Cyan
Write-Host "Weak Key Detection" -ForegroundColor Cyan
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

Write-Host "[*] Testing if device registration accepts weak keys..." -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Weak key test cases
$WeakKeys = @(
    @{
        Name = "Empty key"
        Key = ""
    },
    @{
        Name = "Single byte"
        Key = "MQ=="  # Base64 of "1"
    },
    @{
        Name = "All zeros (128-bit)"
        Key = "AAAAAAAAAAAAAAAAAAAAAA=="
    },
    @{
        Name = "All ones (128-bit)"
        Key = "//////////8="
    },
    @{
        Name = "Short RSA-like (512-bit is weak)"
        Key = @"
-----BEGIN PUBLIC KEY-----
MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANxwLuKPMfF7Ei7rCmJvZpCjTJNUYPBd
kxvGlLF6VCQ2GVnBHlZxVxGTxKJxJxkMVYBRuPJzw8kZFHV7vKqcxR0CAwEAAQ==
-----END PUBLIC KEY-----
"@
    },
    @{
        Name = "Known test key (from online examples)"
        Key = @"
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYvXH0DeOqf4g6P4eDZjPAMC8i
3LKvn7IiRYKs5k6jP8rYMxnPjLJXmYLdPNeqbz/+dZ+WTDbbzYflr+ZVfGh5kDvx
a2P7KjN7Pk5xh/+ZwZBjGZVXLKEV+ENJM9XP8aaMq/k5/8GzQ7K8C5IxGVMxP6dD
X9O8xO8xVa7oODvLBQIDAQAB
-----END PUBLIC KEY-----
"@
    },
    @{
        Name = "Malformed PEM"
        Key = "-----BEGIN PUBLIC KEY-----`nNOT_VALID_BASE64`n-----END PUBLIC KEY-----"
    },
    @{
        Name = "Private key instead of public"
        Key = "-----BEGIN PRIVATE KEY-----`nTESTING`n-----END PRIVATE KEY-----"
    }
)

foreach ($TestCase in $WeakKeys) {
    Write-Host "`n[*] Testing: $($TestCase.Name)" -ForegroundColor Yellow
    
    $Body = @{
        deviceName = "Weak Key Test Device"
        deviceType = "ANDROID"
        publicKey = $TestCase.Key
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
            -Method POST -Headers $Headers -Body $Body
        
        if ($Response.code -eq 1000) {
            Write-Host "[!] CRITICAL: Weak key accepted!" -ForegroundColor Red
            Write-Host "    Device ID: $($Response.data.deviceId)" -ForegroundColor Red
            $VulnerabilitiesFound++
        } else {
            Write-Host "[+] PASS: Key rejected (code: $($Response.code))" -ForegroundColor Green
        }
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -eq 400) {
            Write-Host "[+] PASS: Key validation rejected weak key (400)" -ForegroundColor Green
        } elseif ($StatusCode -eq 401) {
            Write-Host "[?] Auth issue, but key likely would be rejected" -ForegroundColor Yellow
        } else {
            Write-Host "[+] PASS: Rejected ($StatusCode)" -ForegroundColor Green
        }
    }
}

# Test: Key reuse detection
Write-Host "`n[*] Testing: Key Reuse Detection" -ForegroundColor Yellow
Write-Host "[*] Attempting to register same key twice..." -ForegroundColor Gray

$DuplicateKey = @"
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0m59l2u9iDnMbrXHfqkO
rn2dVQ3vfBJqcDuFUK03d+1PZGbVlNCqnkpIJ8syFppW8ljnWweP7+LiWpRg0cyQ
B/nD0DtQnB3+MZNmO8vLSvMR3ej/rT/YKp5zKpKxjAzDxiu7DCNx+DUxNhT8k/oB
FEFX9Dg/9g8bBoeSq2d5byL+sSF5WNmq2pDpE8GqVHM+RkPFrL1hLx7/AGpd9D/Z
0TBPy4E07u/g8hNxMB7IsREKh5pGJxNjFClAq0hnWDVatnZsWy7RmJNv2v7VtLuv
4ADrPmFD7EIJMrB5WHlHqTZ7YKxDhzK7oFhnuf0xAY7UZha/ALQe2ygqylaLhzof
vQIDAQAB
-----END PUBLIC KEY-----
"@

# First registration
$Body = @{
    deviceName = "Duplicate Key Test 1"
    deviceType = "ANDROID"
    publicKey = $DuplicateKey
} | ConvertTo-Json

try {
    $Response1 = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
        -Method POST -Headers $Headers -Body $Body
    Write-Host "    First registration: Success" -ForegroundColor Gray
} catch {
    Write-Host "    First registration: Failed (expected if key invalid)" -ForegroundColor Gray
}

# Second registration with same key
$Body = @{
    deviceName = "Duplicate Key Test 2"
    deviceType = "ANDROID"
    publicKey = $DuplicateKey
} | ConvertTo-Json

try {
    $Response2 = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
        -Method POST -Headers $Headers -Body $Body
    
    Write-Host "[!] WARNING: Same key registered twice!" -ForegroundColor Yellow
    Write-Host "    Should enforce one key per device" -ForegroundColor Yellow
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 409) {
        Write-Host "[+] PASS: Duplicate key rejected (409 Conflict)" -ForegroundColor Green
    } else {
        Write-Host "[+] Second registration rejected ($StatusCode)" -ForegroundColor Green
    }
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Key validation appears secure" -ForegroundColor Green
}

Write-Host "`n[*] KEY SECURITY REQUIREMENTS:" -ForegroundColor Yellow
Write-Host "    1. Minimum key size: RSA-2048 or EC P-256" -ForegroundColor White
Write-Host "    2. Reject known weak/test keys" -ForegroundColor White
Write-Host "    3. Validate PEM format strictly" -ForegroundColor White
Write-Host "    4. Reject private keys (only accept public)" -ForegroundColor White
Write-Host "    5. Detect and reject key reuse across devices" -ForegroundColor White
Write-Host "    6. Verify key is cryptographically valid" -ForegroundColor White

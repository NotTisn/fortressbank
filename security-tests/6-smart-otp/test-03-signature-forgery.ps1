# Smart OTP Security Tests
# Test 03: Signature Forgery Prevention
# An attacker tries to forge device signatures without the private key

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST 03" -ForegroundColor Cyan
Write-Host "Signature Forgery Prevention" -ForegroundColor Cyan
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

Write-Host "[*] Test: Submit forged signatures" -ForegroundColor Yellow

# Common attack patterns for signature forgery
$ForgedSignatures = @(
    # Empty signature
    "",
    # Invalid base64
    "not-valid-base64!!!",
    # Valid base64 but wrong signature
    "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=",
    # Null bytes
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAo=",
    # Very short signature (truncated)
    "YWI=",
    # Extremely long signature (potential DoS)
    ("A" * 10000)
)

$AllRejected = $true

foreach ($Sig in $ForgedSignatures) {
    $DisplaySig = if ($Sig.Length -gt 50) { $Sig.Substring(0, 50) + "..." } else { $Sig }
    Write-Host "[*] Testing signature: $DisplaySig" -ForegroundColor Gray
    
    $Body = @{
        challengeId = "test-challenge-" + [guid]::NewGuid()
        deviceId = "test-device-id"
        signatureBase64 = $Sig
    } | ConvertTo-Json -Depth 10
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
            -Method POST -Headers $Headers -Body $Body
        
        # Check if server rejected (valid=false means attack blocked)
        if ($Response.data -and $Response.data.valid -eq $false) {
            Write-Host "[+] Rejected: Signature verification failed (valid=false)" -ForegroundColor Green
        } else {
            Write-Host "[!] VULNERABILITY: Server accepted forged signature!" -ForegroundColor Red
            Write-Host "    Signature: $DisplaySig" -ForegroundColor Red
            $AllRejected = $false
        }
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        
        if ($StatusCode -eq 400) {
            Write-Host "[+] Rejected: Invalid signature (400)" -ForegroundColor Green
        } elseif ($StatusCode -eq 401) {
            Write-Host "[+] Rejected: Signature verification failed (401)" -ForegroundColor Green
        } elseif ($StatusCode -eq 404) {
            Write-Host "[+] Rejected: Challenge/device not found (404)" -ForegroundColor Green
        } elseif ($StatusCode -eq 413) {
            Write-Host "[+] Rejected: Payload too large (413)" -ForegroundColor Green
        } else {
            Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
        }
    }
}

# Test signature algorithm confusion
Write-Host "`n[*] Testing algorithm confusion attacks..." -ForegroundColor Yellow

# Try to register a device with weak algorithm
$WeakKeyPatterns = @(
    # RSA-512 (too weak)
    "-----BEGIN PUBLIC KEY-----
MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAK8/P8LsJ8hB5whIb8cL0bvW5zRBQkhv
rPmZPgP1sCfVNLT3w7Ybi8WqR3dZK5X8vR7r6G5uNq3f7q7uFZJ/S/MCAQM=
-----END PUBLIC KEY-----",
    # EC with weak curve
    "-----BEGIN PUBLIC KEY-----
MEkwEwYHKoZIzj0CAQYIKoZIzj0DAQIDMgAE
-----END PUBLIC KEY-----"
)

foreach ($WeakKey in $WeakKeyPatterns) {
    Write-Host "[*] Testing weak key registration..." -ForegroundColor Gray
    
    $Body = @{
        deviceId = "weak-key-device-" + [guid]::NewGuid()
        name = "Weak Key Device"
        publicKeyPem = $WeakKey
    } | ConvertTo-Json
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/register" `
            -Method POST -Headers $Headers -Body $Body
        
        Write-Host "[!] WARNING: Server accepted weak key!" -ForegroundColor Yellow
        Write-Host "    Should validate key strength" -ForegroundColor Yellow
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "[+] Weak key rejected (HTTP $StatusCode)" -ForegroundColor Green
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
if ($AllRejected) {
    Write-Host "RESULT: PASS - Signature forgery protection working" -ForegroundColor Green
} else {
    Write-Host "RESULT: FAIL - Signature validation vulnerable!" -ForegroundColor Red
}
Write-Host "========================================`n" -ForegroundColor Cyan

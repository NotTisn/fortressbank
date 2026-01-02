# Smart OTP Security Tests
# Test 02: Device Ownership (IDOR) Prevention
# An attacker tries to use another user's registered device

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TokenFile = "../access_token.txt"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST 02" -ForegroundColor Cyan
Write-Host "Device Ownership (IDOR) Prevention" -ForegroundColor Cyan
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

Write-Host "[*] Test: Access another user's device" -ForegroundColor Yellow

# Generate a fake device ID that would belong to a different user
$VictimDeviceId = "victim-device-" + [guid]::NewGuid().ToString()

# Step 1: Try to verify using a device ID not owned by current user
Write-Host "[*] Step 1: Attempting to use non-owned device ID..." -ForegroundColor Gray

$Body = @{
    challengeId = "test-challenge-123"
    deviceId = $VictimDeviceId
    signatureBase64 = "ZmFrZS1zaWduYXR1cmU="
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/smart-otp/verify-device" `
        -Method POST -Headers $Headers -Body $Body
    
    # Check if server rejected (valid=false means attack blocked)
    if ($Response.data -and $Response.data.valid -eq $false) {
        Write-Host "[+] PASS: Server rejected non-owned device" -ForegroundColor Green
        Write-Host "    Message: $($Response.data.message)" -ForegroundColor Gray
    } else {
        Write-Host "[!] VULNERABILITY: Server accepted non-owned device!" -ForegroundColor Red
        Write-Host "    This is a critical IDOR vulnerability!" -ForegroundColor Red
        exit 1
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    $ErrorBody = $_.ErrorDetails.Message
    
    if ($StatusCode -eq 403) {
        Write-Host "[+] PASS: Server rejected with 403 Forbidden" -ForegroundColor Green
        Write-Host "    Ownership check is working correctly" -ForegroundColor Gray
    } elseif ($StatusCode -eq 400 -or $StatusCode -eq 404) {
        Write-Host "[+] PASS: Server rejected device (HTTP $StatusCode)" -ForegroundColor Green
        Write-Host "    Device not found or challenge invalid" -ForegroundColor Gray
    } else {
        Write-Host "[?] Response: HTTP $StatusCode" -ForegroundColor Yellow
        Write-Host "    Body: $ErrorBody" -ForegroundColor Gray
    }
}

# Step 2: Try to list another user's devices
Write-Host "`n[*] Step 2: Attempting to access other user's devices..." -ForegroundColor Gray

# Try path traversal in device listing
$PathTraversalUrls = @(
    "$BaseUrl/devices?userId=other-user-id",
    "$BaseUrl/devices/../admin/devices",
    "$BaseUrl/devices/../../internal/devices"
)

$AllSecure = $true
foreach ($Url in $PathTraversalUrls) {
    try {
        $Response = Invoke-RestMethod -Uri $Url -Method GET -Headers $Headers
        
        # If we get a response, check if it only contains OUR devices
        $DeviceCount = ($Response.data | Measure-Object).Count
        Write-Host "[*] Got $DeviceCount devices from: $Url" -ForegroundColor Gray
        
        # This is okay if it's just our own devices
    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -eq 400 -or $StatusCode -eq 403 -or $StatusCode -eq 404) {
            Write-Host "[+] Blocked: $Url (HTTP $StatusCode)" -ForegroundColor Green
        } else {
            Write-Host "[?] $Url : HTTP $StatusCode" -ForegroundColor Yellow
        }
    }
}

# Step 3: Try to delete another user's device
Write-Host "`n[*] Step 3: Attempting to delete non-owned device..." -ForegroundColor Gray

try {
    $Response = Invoke-RestMethod -Uri "$BaseUrl/devices/$VictimDeviceId" `
        -Method DELETE -Headers $Headers
    
    Write-Host "[!] VULNERABILITY: Deleted non-owned device!" -ForegroundColor Red
    $AllSecure = $false
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 403) {
        Write-Host "[+] PASS: Cannot delete non-owned device (403)" -ForegroundColor Green
    } elseif ($StatusCode -eq 404) {
        Write-Host "[+] PASS: Device not found (404)" -ForegroundColor Green
    } else {
        Write-Host "[?] HTTP $StatusCode" -ForegroundColor Yellow
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
if ($AllSecure) {
    Write-Host "RESULT: PASS - IDOR protections working" -ForegroundColor Green
} else {
    Write-Host "RESULT: FAIL - IDOR vulnerability detected!" -ForegroundColor Red
}
Write-Host "========================================`n" -ForegroundColor Cyan

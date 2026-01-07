# Smart OTP Security Tests - Run All
param([string]$BaseUrl = "http://localhost:4000")

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TokenFile = Join-Path $ScriptDir "..\access_token.txt"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SMART OTP SECURITY TEST SUITE" -ForegroundColor Cyan
Write-Host "FortressBank Security Tests - Category 6" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$Tests = @(
    @{ Name = "Challenge Replay"; Script = "test-01-challenge-replay.ps1" }
    @{ Name = "Device Ownership (IDOR)"; Script = "test-02-device-ownership.ps1" }
    @{ Name = "Signature Forgery"; Script = "test-03-signature-forgery.ps1" }
    @{ Name = "Challenge Expiry"; Script = "test-04-challenge-expiry.ps1" }
    @{ Name = "Face Bypass"; Script = "test-05-face-bypass.ps1" }
)

foreach ($Test in $Tests) {
    Write-Host "Running: $($Test.Name)..." -ForegroundColor White
    $ScriptPath = Join-Path $ScriptDir $Test.Script
    if (Test-Path $ScriptPath) {
        & powershell -ExecutionPolicy Bypass -File $ScriptPath -BaseUrl $BaseUrl -TokenFile $TokenFile
    }
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All Smart OTP tests completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

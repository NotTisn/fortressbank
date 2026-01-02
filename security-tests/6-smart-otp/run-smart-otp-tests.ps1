# Smart OTP Security Tests - Run All
# Execute all Smart OTP security tests

param(
    [string]$BaseUrl = "http://localhost:8000"
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host @"

 ███████╗███╗   ███╗ █████╗ ██████╗ ████████╗     ██████╗ ████████╗██████╗ 
 ██╔════╝████╗ ████║██╔══██╗██╔══██╗╚══██╔══╝    ██╔═══██╗╚══██╔══╝██╔══██╗
 ███████╗██╔████╔██║███████║██████╔╝   ██║       ██║   ██║   ██║   ██████╔╝
 ╚════██║██║╚██╔╝██║██╔══██║██╔══██╗   ██║       ██║   ██║   ██║   ██╔═══╝ 
 ███████║██║ ╚═╝ ██║██║  ██║██║  ██║   ██║       ╚██████╔╝   ██║   ██║     
 ╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝        ╚═════╝    ╚═╝   ╚═╝     
                                                                            
 ███████╗███████╗ ██████╗██╗   ██╗██████╗ ██╗████████╗██╗   ██╗            
 ██╔════╝██╔════╝██╔════╝██║   ██║██╔══██╗██║╚══██╔══╝╚██╗ ██╔╝            
 ███████╗█████╗  ██║     ██║   ██║██████╔╝██║   ██║    ╚████╔╝             
 ╚════██║██╔══╝  ██║     ██║   ██║██╔══██╗██║   ██║     ╚██╔╝              
 ███████║███████╗╚██████╗╚██████╔╝██║  ██║██║   ██║      ██║               
 ╚══════╝╚══════╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝   ╚═╝      ╚═╝               
                                                                            
                    FortressBank Security Test Suite                        
                    Category 6: Smart OTP Security                          
                                                                            
"@ -ForegroundColor Cyan

Write-Host "========================================" -ForegroundColor DarkGray
Write-Host "Target: $BaseUrl" -ForegroundColor DarkGray
Write-Host "Started: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "========================================`n" -ForegroundColor DarkGray

# Check for token
$TokenFile = Join-Path $ScriptDir "..\access_token.txt"
if (-not (Test-Path $TokenFile)) {
    Write-Host "[!] No access token found. Running setup..." -ForegroundColor Yellow
    $SetupScript = Join-Path $ScriptDir "..\setup-testuser.ps1"
    if (Test-Path $SetupScript) {
        & $SetupScript
    } else {
        Write-Host "[!] setup-testuser.ps1 not found. Please create access_token.txt" -ForegroundColor Red
        exit 1
    }
}

# Run all tests
$Tests = @(
    @{ Name = "Challenge Replay Attack"; Script = "test-01-challenge-replay.ps1" }
    @{ Name = "Device Ownership (IDOR)"; Script = "test-02-device-ownership.ps1" }
    @{ Name = "Signature Forgery"; Script = "test-03-signature-forgery.ps1" }
    @{ Name = "Challenge Expiry & Rate Limit"; Script = "test-04-challenge-expiry.ps1" }
    @{ Name = "Face Verification Bypass"; Script = "test-05-face-bypass.ps1" }
)

$Results = @()

foreach ($Test in $Tests) {
    Write-Host "`n" -NoNewline
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
    Write-Host "Running: $($Test.Name)" -ForegroundColor White
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
    
    $ScriptPath = Join-Path $ScriptDir $Test.Script
    
    if (Test-Path $ScriptPath) {
        try {
            & $ScriptPath -BaseUrl $BaseUrl -TokenFile $TokenFile
            $Results += @{ Test = $Test.Name; Status = "Completed" }
        } catch {
            Write-Host "[!] Test failed with error: $_" -ForegroundColor Red
            $Results += @{ Test = $Test.Name; Status = "Error" }
        }
    } else {
        Write-Host "[!] Script not found: $ScriptPath" -ForegroundColor Red
        $Results += @{ Test = $Test.Name; Status = "Not Found" }
    }
}

# Summary
Write-Host "`n`n" -NoNewline
Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                     TEST SUMMARY                             ║" -ForegroundColor Cyan
Write-Host "╠══════════════════════════════════════════════════════════════╣" -ForegroundColor Cyan

foreach ($Result in $Results) {
    $StatusColor = switch ($Result.Status) {
        "Completed" { "Green" }
        "Error" { "Red" }
        default { "Yellow" }
    }
    $Line = "║ {0,-40} {1,-15} ║" -f $Result.Test, $Result.Status
    Write-Host $Line -ForegroundColor $StatusColor
}

Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nCompleted: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "`nSmart OTP Security Tests Complete!`n" -ForegroundColor Green

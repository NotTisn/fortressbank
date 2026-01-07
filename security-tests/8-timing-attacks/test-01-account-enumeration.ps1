# Timing & Side-Channel Attacks
# Test 01: Account Enumeration via Timing
# Detects valid vs invalid accounts by measuring response time

param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$KeycloakUrl = "http://localhost:8888"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TIMING ATTACK TEST 01" -ForegroundColor Cyan
Write-Host "Account Enumeration via Response Time" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "[*] This test measures response times to detect user enumeration" -ForegroundColor Yellow
Write-Host "[*] Valid users may have different timing than invalid users" -ForegroundColor Yellow

$Results = @()

# Test accounts - mix of likely valid and definitely invalid
$TestAccounts = @(
    @{ username = "testuser"; type = "likely_valid" },
    @{ username = "admin"; type = "likely_valid" },
    @{ username = "user"; type = "likely_valid" },
    @{ username = "xq9k2m3n4p5"; type = "definitely_invalid" },
    @{ username = "nonexistent_user_12345"; type = "definitely_invalid" },
    @{ username = "fake.user.nobody@test.com"; type = "definitely_invalid" }
)

Write-Host "`n[*] Testing login timing for various usernames..." -ForegroundColor Gray

foreach ($Account in $TestAccounts) {
    $Username = $Account.username
    $Type = $Account.type
    
    $Timings = @()
    
    # Multiple attempts for statistical significance
    for ($i = 1; $i -le 5; $i++) {
        $Body = @{
            grant_type = "password"
            client_id = "kong"
            client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
            username = $Username
            password = "wrongpassword123"
        }
        
        $Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        
        try {
            $Response = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fortressbank-realm/protocol/openid-connect/token" `
                -Method POST -Body $Body -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10
        } catch {
            # Expected - wrong password
        }
        
        $Stopwatch.Stop()
        $Timings += $Stopwatch.ElapsedMilliseconds
        
        Start-Sleep -Milliseconds 100  # Small delay between attempts
    }
    
    $AvgTime = ($Timings | Measure-Object -Average).Average
    $MinTime = ($Timings | Measure-Object -Minimum).Minimum
    $MaxTime = ($Timings | Measure-Object -Maximum).Maximum
    
    $Results += @{
        Username = $Username
        Type = $Type
        AvgMs = [math]::Round($AvgTime, 2)
        MinMs = $MinTime
        MaxMs = $MaxTime
    }
    
    Write-Host "    [$Type] $Username : Avg=${AvgTime}ms (Min=$MinTime, Max=$MaxTime)" -ForegroundColor Gray
}

# Analyze results
Write-Host "`n[*] ANALYSIS:" -ForegroundColor Yellow

$ValidAvg = ($Results | Where-Object { $_.Type -eq "likely_valid" } | Measure-Object -Property AvgMs -Average).Average
$InvalidAvg = ($Results | Where-Object { $_.Type -eq "definitely_invalid" } | Measure-Object -Property AvgMs -Average).Average

$TimingDiff = [math]::Abs($ValidAvg - $InvalidAvg)
$TimingPercent = if ($ValidAvg -gt 0) { [math]::Round(($TimingDiff / $ValidAvg) * 100, 2) } else { 0 }

Write-Host "    Likely Valid Users Avg: $([math]::Round($ValidAvg, 2))ms" -ForegroundColor White
Write-Host "    Definitely Invalid Avg: $([math]::Round($InvalidAvg, 2))ms" -ForegroundColor White
Write-Host "    Timing Difference: ${TimingDiff}ms ($TimingPercent%)" -ForegroundColor White

if ($TimingDiff -gt 50) {
    Write-Host "`n[!] VULNERABILITY: Significant timing difference detected!" -ForegroundColor Red
    Write-Host "    Attackers can enumerate valid usernames by measuring response time" -ForegroundColor Red
    Write-Host "    Difference > 50ms is exploitable" -ForegroundColor Red
} elseif ($TimingDiff -gt 20) {
    Write-Host "`n[!] WARNING: Marginal timing difference" -ForegroundColor Yellow
    Write-Host "    May be exploitable with statistical analysis" -ForegroundColor Yellow
} else {
    Write-Host "`n[+] PASS: Timing is consistent (difference < 20ms)" -ForegroundColor Green
    Write-Host "    Constant-time comparison appears to be in use" -ForegroundColor Green
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "RECOMMENDATIONS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n[*] To prevent timing attacks:" -ForegroundColor Yellow
Write-Host "    1. Use constant-time comparison for password checks" -ForegroundColor White
Write-Host "    2. Add random delay jitter to responses" -ForegroundColor White
Write-Host "    3. Return identical messages for valid/invalid users" -ForegroundColor White
Write-Host "    4. Consider CAPTCHA after failed attempts" -ForegroundColor White
Write-Host "    5. Implement account lockout policies" -ForegroundColor White

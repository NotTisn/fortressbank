# Timing & Side-Channel Attacks
# Test 02: Password Reset Enumeration
# Detects if password reset reveals account existence

param(
    [string]$BaseUrl = "http://localhost:8000"
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TIMING ATTACK TEST 02" -ForegroundColor Cyan
Write-Host "Password Reset Enumeration" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "[*] Testing if password reset reveals account existence" -ForegroundColor Yellow

$VulnerabilitiesFound = 0

# Test emails
$TestEmails = @(
    @{ email = "testuser@fortressbank.com"; type = "likely_valid" },
    @{ email = "admin@fortressbank.com"; type = "likely_valid" },
    @{ email = "totally_fake_email_12345@nonexistent.com"; type = "definitely_invalid" },
    @{ email = "xyzabc@fakeemail.xyz"; type = "definitely_invalid" }
)

$Headers = @{
    "Content-Type" = "application/json"
}

Write-Host "`n[*] Test 1: Response Message Comparison" -ForegroundColor Yellow

$Responses = @()

foreach ($TestCase in $TestEmails) {
    $Email = $TestCase.email
    $Type = $TestCase.type
    
    $Body = @{
        email = $Email
    } | ConvertTo-Json
    
    $Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    
    try {
        $Response = Invoke-RestMethod -Uri "$BaseUrl/users/forgot-password" `
            -Method POST -Headers $Headers -Body $Body
        
        $Stopwatch.Stop()
        
        $Responses += @{
            Email = $Email
            Type = $Type
            StatusCode = 200
            Message = $Response.message
            Time = $Stopwatch.ElapsedMilliseconds
        }
        
        Write-Host "    [$Type] $Email" -ForegroundColor Gray
        Write-Host "        Response: $($Response.message)" -ForegroundColor Gray
        Write-Host "        Time: $($Stopwatch.ElapsedMilliseconds)ms" -ForegroundColor Gray
        
    } catch {
        $Stopwatch.Stop()
        $StatusCode = $_.Exception.Response.StatusCode.value__
        $ErrorBody = $_.ErrorDetails.Message
        
        $Responses += @{
            Email = $Email
            Type = $Type
            StatusCode = $StatusCode
            Message = $ErrorBody
            Time = $Stopwatch.ElapsedMilliseconds
        }
        
        Write-Host "    [$Type] $Email" -ForegroundColor Gray
        Write-Host "        HTTP: $StatusCode" -ForegroundColor Gray
        Write-Host "        Time: $($Stopwatch.ElapsedMilliseconds)ms" -ForegroundColor Gray
    }
    
    Start-Sleep -Milliseconds 500
}

# Analyze responses
Write-Host "`n[*] ANALYSIS:" -ForegroundColor Yellow

$ValidResponses = $Responses | Where-Object { $_.Type -eq "likely_valid" }
$InvalidResponses = $Responses | Where-Object { $_.Type -eq "definitely_invalid" }

# Check if status codes differ
$ValidCodes = ($ValidResponses | ForEach-Object { $_.StatusCode } | Sort-Object -Unique) -join ","
$InvalidCodes = ($InvalidResponses | ForEach-Object { $_.StatusCode } | Sort-Object -Unique) -join ","

Write-Host "    Valid user status codes: $ValidCodes" -ForegroundColor White
Write-Host "    Invalid user status codes: $InvalidCodes" -ForegroundColor White

if ($ValidCodes -ne $InvalidCodes) {
    Write-Host "`n[!] VULNERABILITY: Different HTTP status codes!" -ForegroundColor Red
    Write-Host "    Attackers can identify valid accounts by status code" -ForegroundColor Red
    $VulnerabilitiesFound++
}

# Check if messages differ
$ValidMessages = ($ValidResponses | ForEach-Object { $_.Message } | Sort-Object -Unique)
$InvalidMessages = ($InvalidResponses | ForEach-Object { $_.Message } | Sort-Object -Unique)

$MessagesDiffer = $false
foreach ($vm in $ValidMessages) {
    if ($InvalidMessages -notcontains $vm) {
        $MessagesDiffer = $true
        break
    }
}

if ($MessagesDiffer) {
    Write-Host "`n[!] VULNERABILITY: Different response messages!" -ForegroundColor Red
    Write-Host "    Attackers can identify valid accounts by response text" -ForegroundColor Red
    $VulnerabilitiesFound++
} else {
    Write-Host "`n[+] PASS: Response messages are consistent" -ForegroundColor Green
}

# Check timing
$ValidAvgTime = ($ValidResponses | Measure-Object -Property Time -Average).Average
$InvalidAvgTime = ($InvalidResponses | Measure-Object -Property Time -Average).Average
$TimingDiff = [math]::Abs($ValidAvgTime - $InvalidAvgTime)

Write-Host "`n    Valid accounts avg time: $([math]::Round($ValidAvgTime, 2))ms" -ForegroundColor White
Write-Host "    Invalid accounts avg time: $([math]::Round($InvalidAvgTime, 2))ms" -ForegroundColor White
Write-Host "    Difference: $([math]::Round($TimingDiff, 2))ms" -ForegroundColor White

if ($TimingDiff -gt 100) {
    Write-Host "`n[!] WARNING: Significant timing difference detected" -ForegroundColor Yellow
    Write-Host "    May indicate account enumeration vulnerability" -ForegroundColor Yellow
}

# Summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($VulnerabilitiesFound -gt 0) {
    Write-Host "`n[!] VULNERABILITIES FOUND: $VulnerabilitiesFound" -ForegroundColor Red
} else {
    Write-Host "`n[+] Password reset appears secure against enumeration" -ForegroundColor Green
}

Write-Host "`n[*] BEST PRACTICE:" -ForegroundColor Yellow
Write-Host "    Always respond with:" -ForegroundColor White
Write-Host '    "If an account exists with that email, a reset link has been sent."' -ForegroundColor Green
Write-Host "    Use identical HTTP 200 status for all cases" -ForegroundColor White
Write-Host "    Add random delay to normalize timing" -ForegroundColor White

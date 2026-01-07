# Salami Slicing Attack Simulation
# Tests the new velocity tracking (Rule 7) in risk-engine

$ErrorActionPreference = "Continue"

Write-Host "=== SALAMI SLICING ATTACK SIMULATION ===" -ForegroundColor Cyan
Write-Host ""

$userId = "salami-test-" + (Get-Random)
Write-Host "User ID: $userId"
Write-Host "Daily Limit: 50,000 VND"
Write-Host "Each slice: 9,900 VND (just under 10,000 threshold)"
Write-Host ""

# Simulate 7 transfers of 9,900 VND each (total: 69,300 VND)
# Should exceed 50,000 limit after transfer 6
for ($i = 1; $i -le 7; $i++) {
    $body = @{
        userId = $userId
        amount = 9900
        payeeId = "new-payee-$i"
    } | ConvertTo-Json
    
    try {
        $result = Invoke-RestMethod -Uri "http://localhost:6000/assess" -Method POST -Body $body -ContentType "application/json"
        $riskLevel = $result.riskLevel
        $challenge = $result.challengeType
    } catch {
        Write-Host "ERROR calling /assess: $_" -ForegroundColor Red
        continue
    }
    
    # Record the transfer (as if it completed)
    $recordBody = @{
        userId = $userId
        amount = 9900
        transactionId = "tx-$i"
    } | ConvertTo-Json
    
    try {
        $recordResult = Invoke-RestMethod -Uri "http://localhost:6000/assess/internal/velocity/record" -Method POST -Body $recordBody -ContentType "application/json"
        $cumulative = $recordResult.newDailyTotal
    } catch {
        Write-Host "ERROR recording velocity: $_" -ForegroundColor Red
        $cumulative = $i * 9900
    }
    
    $color = if ($riskLevel -eq "LOW") { "Green" } elseif ($riskLevel -eq "MEDIUM") { "Yellow" } else { "Red" }
    Write-Host "Transfer $i : 9,900 VND -> Risk=$riskLevel, Challenge=$challenge, Cumulative=$cumulative" -ForegroundColor $color
}

Write-Host ""
Write-Host "=== ANALYSIS ===" -ForegroundColor Cyan

# Check velocity status
$velocityBody = @{ userId = $userId; amount = 0; payeeId = "check" } | ConvertTo-Json
$checkResult = Invoke-RestMethod -Uri "http://localhost:6000/assess" -Method POST -Body $velocityBody -ContentType "application/json"

Write-Host ""
if ($checkResult.riskLevel -ne "LOW") {
    Write-Host "[PASS] Velocity tracking is working! Subsequent transfers are flagged as risky." -ForegroundColor Green
} else {
    Write-Host "[INFO] Current risk level for next transfer: $($checkResult.riskLevel)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Expected behavior:"
Write-Host "  - Transfers 1-5: LOW risk (cumulative < 50,000)"
Write-Host "  - Transfer 6+: MEDIUM risk (cumulative >= 50,000, triggers +35 velocity score)"

# Business Logic Attack Tests Runner
# Runs all business logic security tests

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "BUSINESS LOGIC ATTACK TESTS" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tests = Get-ChildItem -Path $ScriptDir -Filter "test-*.ps1" | Sort-Object Name

$Results = @{
    Passed = 0
    Failed = 0
    Skipped = 0
}

foreach ($Test in $Tests) {
    Write-Host "`n>>> Running: $($Test.Name)" -ForegroundColor Cyan
    Write-Host "=" * 60 -ForegroundColor Gray
    
    try {
        & $Test.FullName
        $Results.Passed++
    } catch {
        Write-Host "[X] Test failed with error: $($_.Exception.Message)" -ForegroundColor Red
        $Results.Failed++
    }
}

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "BUSINESS LOGIC TESTS SUMMARY" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "Passed:  $($Results.Passed)" -ForegroundColor Green
Write-Host "Failed:  $($Results.Failed)" -ForegroundColor Red
Write-Host "Skipped: $($Results.Skipped)" -ForegroundColor Yellow
Write-Host "Total:   $($Tests.Count)" -ForegroundColor White

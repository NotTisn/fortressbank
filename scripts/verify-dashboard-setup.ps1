# ============================================
# verify-dashboard-setup.ps1
# Script để verify Admin Dashboard setup
# ============================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Admin Dashboard Setup Verification   " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Colors
$Success = "Green"
$Error = "Red"
$Warning = "Yellow"
$Info = "Cyan"

# Step 1: Check Account Service Database
Write-Host "[1/4] Checking Account Service..." -ForegroundColor $Info

try {
    $accountCount = & psql -U postgres -d account_db -t -c "SELECT COUNT(*) FROM accounts WHERE account_id LIKE 'acc-%';" 2>$null
    $accountCount = $accountCount.Trim()
    
    if ($accountCount -eq "20") {
        Write-Host "  ✓ Found $accountCount test accounts" -ForegroundColor $Success
        Write-Host "  ✓ Account migration V7 successful!" -ForegroundColor $Success
    }
    elseif ($accountCount -gt 0) {
        Write-Host "  ⚠ Found $accountCount accounts (expected 20)" -ForegroundColor $Warning
        Write-Host "  → Migration may be incomplete" -ForegroundColor $Warning
    }
    else {
        Write-Host "  ✗ No test accounts found!" -ForegroundColor $Error
        Write-Host "  → Run: cd account-service && mvn spring-boot:run" -ForegroundColor $Warning
        $hasError = $true
    }
}
catch {
    Write-Host "  ✗ Cannot connect to account_db" -ForegroundColor $Error
    Write-Host "  → Check if PostgreSQL is running" -ForegroundColor $Warning
    $hasError = $true
}

Write-Host ""

# Step 2: Check Transaction Service Database
Write-Host "[2/4] Checking Transaction Service..." -ForegroundColor $Info

try {
    $txnCount = & psql -U postgres -d transaction_db -t -c "SELECT COUNT(*) FROM transactions;" 2>$null
    $txnCount = $txnCount.Trim()
    
    if ([int]$txnCount -ge 75) {
        Write-Host "  ✓ Found $txnCount transactions" -ForegroundColor $Success
        Write-Host "  ✓ Transaction migration V6 successful!" -ForegroundColor $Success
    }
    elseif ([int]$txnCount -gt 0) {
        Write-Host "  ⚠ Found $txnCount transactions (expected 75+)" -ForegroundColor $Warning
        Write-Host "  → Migration may be incomplete" -ForegroundColor $Warning
    }
    else {
        Write-Host "  ✗ No transactions found!" -ForegroundColor $Error
        Write-Host "  → Run: cd transaction-service && mvn spring-boot:run" -ForegroundColor $Warning
        $hasError = $true
    }
}
catch {
    Write-Host "  ✗ Cannot connect to transaction_db" -ForegroundColor $Error
    Write-Host "  → Check if PostgreSQL is running" -ForegroundColor $Warning
    $hasError = $true
}

Write-Host ""

# Step 3: Check Migration History
Write-Host "[3/4] Checking Migration History..." -ForegroundColor $Info

try {
    $accountMigration = & psql -U postgres -d account_db -t -c "SELECT version FROM flyway_schema_history WHERE version = '7';" 2>$null
    if ($accountMigration) {
        Write-Host "  ✓ Account migration V7 in history" -ForegroundColor $Success
    }
    else {
        Write-Host "  ✗ Account migration V7 not found in history" -ForegroundColor $Error
        $hasError = $true
    }
    
    $txnMigration = & psql -U postgres -d transaction_db -t -c "SELECT version FROM flyway_schema_history WHERE version = '6';" 2>$null
    if ($txnMigration) {
        Write-Host "  ✓ Transaction migration V6 in history" -ForegroundColor $Success
    }
    else {
        Write-Host "  ✗ Transaction migration V6 not found in history" -ForegroundColor $Error
        $hasError = $true
    }
}
catch {
    Write-Host "  ✗ Cannot check migration history" -ForegroundColor $Error
    $hasError = $true
}

Write-Host ""

# Step 4: Check Services Running
Write-Host "[4/4] Checking Services..." -ForegroundColor $Info

$accountService = Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue
if ($accountService) {
    Write-Host "  ✓ Account Service running on port 8081" -ForegroundColor $Success
}
else {
    Write-Host "  ⚠ Account Service not running on port 8081" -ForegroundColor $Warning
}

$txnService = Get-NetTCPConnection -LocalPort 8082 -ErrorAction SilentlyContinue
if ($txnService) {
    Write-Host "  ✓ Transaction Service running on port 8082" -ForegroundColor $Success
}
else {
    Write-Host "  ⚠ Transaction Service not running on port 8082" -ForegroundColor $Warning
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

# Summary
if ($hasError) {
    Write-Host "❌ SETUP INCOMPLETE" -ForegroundColor $Error
    Write-Host ""
    Write-Host "Please follow these steps:" -ForegroundColor $Warning
    Write-Host "1. cd account-service && mvn clean spring-boot:run" -ForegroundColor White
    Write-Host "2. Wait for startup, then verify 20 accounts exist" -ForegroundColor White
    Write-Host "3. cd ../transaction-service && mvn clean spring-boot:run" -ForegroundColor White
    Write-Host "4. Run this script again to verify" -ForegroundColor White
    Write-Host ""
    Write-Host "See: ADMIN-DASHBOARD-QUICKSTART.md for details" -ForegroundColor $Info
}
else {
    Write-Host "✅ ALL CHECKS PASSED!" -ForegroundColor $Success
    Write-Host ""
    Write-Host "Dashboard is ready! Next steps:" -ForegroundColor $Success
    Write-Host "1. Get admin JWT token from Keycloak" -ForegroundColor White
    Write-Host "2. Test API: GET /transactions/admin/dashboard/statistics" -ForegroundColor White
    Write-Host "3. Frontend team can start implementation" -ForegroundColor White
    Write-Host ""
    Write-Host "API Documentation: doc/ADMIN-DASHBOARD-API.md" -ForegroundColor $Info
}

Write-Host "========================================" -ForegroundColor Cyan

# Additional Info
Write-Host ""
Write-Host "📊 Quick Stats:" -ForegroundColor $Info
Write-Host "  - Test Accounts: $accountCount" -ForegroundColor White
Write-Host "  - Sample Transactions: $txnCount" -ForegroundColor White
Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

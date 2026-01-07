# Setup test user for security testing
param(
    [string]$Password = "password123"
)

$ErrorActionPreference = "Stop"

Write-Host "[SETUP] Setting up testuser for security testing..." -ForegroundColor Cyan

# Get admin token
$adminBody = @{
    client_id = "admin-cli"
    username = "admin"
    password = "admin"
    grant_type = "password"
}
$adminResponse = Invoke-RestMethod -Uri "http://localhost:8888/realms/master/protocol/openid-connect/token" -Method POST -Body $adminBody -ContentType "application/x-www-form-urlencoded"
$adminToken = $adminResponse.access_token
Write-Host "  Got admin token" -ForegroundColor Green

# Find testuser
$headers = @{ Authorization = "Bearer $adminToken" }
$users = Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/users?username=testuser" -Headers $headers

if ($users.Count -eq 0) {
    Write-Host "  Creating testuser..." -ForegroundColor Yellow
    $newUser = @{
        username = "testuser"
        email = "testuser@fortressbank.com"
        enabled = $true
        emailVerified = $true
        firstName = "Test"
        lastName = "User"
        credentials = @(
            @{
                type = "password"
                value = $Password
                temporary = $false
            }
        )
    } | ConvertTo-Json -Depth 3
    
    $createHeaders = @{ 
        Authorization = "Bearer $adminToken"
        "Content-Type" = "application/json"
    }
    Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/users" -Method POST -Body $newUser -Headers $createHeaders
    
    # Get the new user
    $users = Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/users?username=testuser" -Headers $headers
}

$userId = $users[0].id
Write-Host "  Found testuser: $userId" -ForegroundColor Green

# Reset password
$resetBody = @{
    type = "password"
    value = $Password
    temporary = $false
} | ConvertTo-Json

$resetHeaders = @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
}
Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/users/$userId/reset-password" -Method PUT -Body $resetBody -Headers $resetHeaders
Write-Host "  Password reset to: $Password" -ForegroundColor Green

# Assign 'user' role
$roles = Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/roles" -Headers $headers
$userRole = $roles | Where-Object { $_.name -eq "user" }

if ($userRole) {
    $roleBody = @($userRole) | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "http://localhost:8888/admin/realms/fortressbank-realm/users/$userId/role-mappings/realm" -Method POST -Body $roleBody -Headers $resetHeaders
        Write-Host "  Assigned 'user' role" -ForegroundColor Green
    } catch {
        Write-Host "  Role already assigned or error: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Test login
Write-Host ""
Write-Host "[TEST] Testing login..." -ForegroundColor Cyan
$loginBody = @{
    client_id = "kong"
    client_secret = "pmFStZwGO8sb0mBDkZmP5niE3wmEELqe"
    username = "testuser"
    password = $Password
    grant_type = "password"
}

try {
    $loginResponse = Invoke-RestMethod -Uri "http://localhost:8888/realms/fortressbank-realm/protocol/openid-connect/token" -Method POST -Body $loginBody -ContentType "application/x-www-form-urlencoded"
    Write-Host "[OK] Login successful! Token expires in $($loginResponse.expires_in)s" -ForegroundColor Green
    
    # Save token for other tests
    $loginResponse.access_token | Out-File -FilePath "$PSScriptRoot\access_token.txt" -NoNewline
    Write-Host "  Token saved to: $PSScriptRoot\access_token.txt" -ForegroundColor Gray
    
    # Output for use in pipeline
    return $loginResponse.access_token
} catch {
    Write-Host "[FAIL] Login failed: $($_.Exception.Message)" -ForegroundColor Red
    throw
}

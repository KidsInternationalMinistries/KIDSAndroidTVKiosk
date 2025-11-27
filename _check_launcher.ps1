# Quick Launcher Status Check and Reset
# Simple script to check current launcher status and fix common issues

Write-Host "KIDS TV Kiosk - Quick Launcher Status" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

# Check if device is connected
$devices = & ".\platform-tools\adb.exe" devices 2>$null
if ($LASTEXITCODE -ne 0 -or -not ($devices | Select-String "device$")) {
    Write-Host "No Android device connected!" -ForegroundColor Red
    Write-Host "Connect your Android TV and enable USB debugging." -ForegroundColor Yellow
    exit 1
}

Write-Host "Device connected" -ForegroundColor Green

# Quick status check
Write-Host "`nCurrent Status:" -ForegroundColor Yellow

# Check HOME role
$homeRoleOutput = & ".\platform-tools\adb.exe" shell dumpsys role
$isCorrectLauncher = $homeRoleOutput -match "android\.app\.role\.HOME" -and $homeRoleOutput -match "holders=com\.kidsim\.tvkiosk"

if ($isCorrectLauncher) {
    Write-Host "HOME Role: KIDS Kiosk (CORRECT)" -ForegroundColor Green
} else {
    Write-Host "HOME Role: NOT SET CORRECTLY" -ForegroundColor Red
    # Try to find what's currently assigned
    $homeLines = $homeRoleOutput | Select-String -Pattern "holders=" 
    if ($homeLines) {
        $homeLines | ForEach-Object { Write-Host "Found: $_" -ForegroundColor Yellow }
    }
}

# Check component state
$componentState = & ".\platform-tools\adb.exe" shell pm list packages -e | Select-String "com.kidsim.tvkiosk"
if ($componentState) {
    Write-Host "App Package: Enabled" -ForegroundColor Green
} else {
    Write-Host "App Package: Not found or disabled" -ForegroundColor Red
}

# Check MainActivityHome component
$aliasState = & ".\platform-tools\adb.exe" shell dumpsys package com.kidsim.tvkiosk | Select-String "MainActivityHome.*enabled"
if ($aliasState) {
    Write-Host "MainActivityHome: Enabled" -ForegroundColor Green
} else {
    Write-Host "MainActivityHome: Not enabled" -ForegroundColor Red
}

Write-Host "`n========================================" -ForegroundColor Cyan

if ($isCorrectLauncher) {
    Write-Host "AutoStart should be working!" -ForegroundColor Green
    Write-Host "If not working, try rebooting the device." -ForegroundColor Yellow
} else {
    Write-Host "AutoStart needs to be fixed." -ForegroundColor Red
    Write-Host "`nQuick Fix Options:" -ForegroundColor Cyan
    Write-Host "1. Run: .\\_force_set_launcher.ps1" -ForegroundColor Yellow
    Write-Host "2. Manual: Settings > Apps > Default Apps > Home App > KIDS Kiosk Bootstrap" -ForegroundColor Yellow
    Write-Host "3. ADB Command: .\platform-tools\adb.exe shell cmd role add-role-holder android.app.role.HOME com.kidsim.tvkiosk" -ForegroundColor Yellow
}
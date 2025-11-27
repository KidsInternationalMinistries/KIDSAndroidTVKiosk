# KIDS TV Kiosk - Device Setup Status
# Quick diagnostics script for Android TV launcher configuration

Write-Host "KIDS TV Kiosk - Device Setup Status" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan

# Check ADB connection
$devices = & ".\platform-tools\adb.exe" devices 2>$null
if ($LASTEXITCODE -ne 0 -or -not ($devices | Select-String "device$")) {
    Write-Host "Status: NO DEVICE CONNECTED" -ForegroundColor Red
    Write-Host "Action: Connect your Android TV and enable USB debugging" -ForegroundColor Yellow
    exit 1
}

Write-Host "Device: CONNECTED" -ForegroundColor Green

# Check HOME role assignment
$homeRoleOutput = & ".\platform-tools\adb.exe" shell dumpsys role
$isCorrectLauncher = $homeRoleOutput -match "android\.app\.role\.HOME" -and $homeRoleOutput -match "holders=com\.kidsim\.tvkiosk"

if ($isCorrectLauncher) {
    Write-Host "HOME Role: KIDS KIOSK (CORRECT)" -ForegroundColor Green
} else {
    Write-Host "HOME Role: WRONG LAUNCHER" -ForegroundColor Red
    # Try to find what's currently assigned
    $homeSection = ($homeRoleOutput | Select-String -Pattern "android\.app\.role\.HOME" -Context 3).ToString()
    if ($homeSection -match "holders=(.+)") {
        $currentApp = $matches[1]
        Write-Host "Current: $currentApp" -ForegroundColor Yellow
    }
}

# Check if app is installed and enabled
$appInstalled = & ".\platform-tools\adb.exe" shell pm list packages | Select-String "com.kidsim.tvkiosk"
if ($appInstalled) {
    Write-Host "App Status: INSTALLED" -ForegroundColor Green
} else {
    Write-Host "App Status: NOT INSTALLED" -ForegroundColor Red
}

# Check component state
$componentEnabled = & ".\platform-tools\adb.exe" shell pm list packages -e | Select-String "com.kidsim.tvkiosk"
if ($componentEnabled) {
    Write-Host "Component: ENABLED" -ForegroundColor Green
} else {
    Write-Host "Component: DISABLED" -ForegroundColor Red
}

Write-Host "`n" + "="*40 -ForegroundColor Cyan

# Final assessment
if ($isCorrectLauncher -and $appInstalled -and $componentEnabled) {
    Write-Host "OVERALL STATUS: WORKING" -ForegroundColor Green
    Write-Host "AutoStart should work after reboot" -ForegroundColor Yellow
    Write-Host "`nTo test: Reboot device and check if KIDS Kiosk starts automatically" -ForegroundColor Cyan
} else {
    Write-Host "OVERALL STATUS: NEEDS SETUP" -ForegroundColor Red
    Write-Host "`nTo fix run: .\\_force_set_launcher.ps1" -ForegroundColor Yellow
    Write-Host "Then reboot device to test" -ForegroundColor Yellow
}

Write-Host "`nOther commands:" -ForegroundColor Gray
Write-Host ".\\_check_launcher.ps1 - Detailed status check" -ForegroundColor Gray
Write-Host ".\\_force_set_launcher.ps1 -Remove - Remove launcher" -ForegroundColor Gray
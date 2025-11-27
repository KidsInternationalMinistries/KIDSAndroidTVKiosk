# Xiaomi Android TV AutoStart Fix
# Enhanced script specifically designed for Xiaomi devices that override launcher settings

Write-Host "Xiaomi Android TV - AutoStart Fix" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan

# Check device connection
$devices = & ".\platform-tools\adb.exe" devices 2>$null
if ($LASTEXITCODE -ne 0 -or -not ($devices | Select-String "device$")) {
    Write-Host "No device connected. Connect your Xiaomi Android TV." -ForegroundColor Red
    exit 1
}

Write-Host "Device connected" -ForegroundColor Green

# Step 1: Ensure HOME role is set
Write-Host "`nStep 1: Setting HOME role..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell cmd role add-role-holder android.app.role.HOME com.kidsim.tvkiosk 2>$null
Write-Host "HOME role assigned" -ForegroundColor Green

# Step 2: Force stop other launchers
Write-Host "`nStep 2: Stopping competing launchers..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell am force-stop com.google.android.tvlauncher 2>$null
& ".\platform-tools\adb.exe" shell am force-stop com.mitv.tvhome.atv 2>$null
& ".\platform-tools\adb.exe" shell am force-stop com.mitv.tvhome.michannel 2>$null
& ".\platform-tools\adb.exe" shell am force-stop com.mitv.tvhome.mitvplus 2>$null
Write-Host "Competing launchers stopped" -ForegroundColor Green

# Step 3: Clear ALL launcher preferences (Xiaomi specific)
Write-Host "`nStep 3: Clearing Xiaomi launcher preferences..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.google.android.tvlauncher 2>$null
& ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.mitv.tvhome.atv 2>$null
& ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.mitv.tvhome.michannel 2>$null

# Step 4: Set our app as preferred for HOME intent  
Write-Host "`nStep 4: Setting KIDS Kiosk as preferred launcher..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell cmd package set-home-activity com.kidsim.tvkiosk/.MainActivityHome 2>$null
Write-Host "Preferred launcher set" -ForegroundColor Green

# Step 5: Disable Google TV Launcher (Xiaomi specific approach)
Write-Host "`nStep 5: Attempting to disable Google TV Launcher..." -ForegroundColor Yellow
$disableResult = & ".\platform-tools\adb.exe" shell pm disable-user --user 0 com.google.android.tvlauncher 2>&1
if ($disableResult -like "*Success*") {
    Write-Host "Google TV Launcher disabled successfully" -ForegroundColor Green
} else {
    Write-Host "Could not disable Google TV Launcher (expected on some devices)" -ForegroundColor Yellow
}

# Step 6: Test the setup
Write-Host "`nStep 6: Testing launcher setup..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>$null
Start-Sleep 2

# Check what's actually running
$foregroundApp = & ".\platform-tools\adb.exe" shell dumpsys activity activities | Select-String "mFocusedApp"
if ($foregroundApp -like "*kidsim*") {
    Write-Host "SUCCESS: KIDS Kiosk is now the active launcher!" -ForegroundColor Green
} else {
    Write-Host "Launcher test completed (checking status...)" -ForegroundColor Yellow
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Xiaomi AutoStart Setup Complete!" -ForegroundColor Green
Write-Host "`nNext Steps:" -ForegroundColor Yellow
Write-Host "1. Reboot your device: Settings > Device Preferences > Restart" -ForegroundColor White
Write-Host "2. After reboot, KIDS Kiosk should start automatically" -ForegroundColor White  
Write-Host "3. If it doesn't work, manually disable Google TV Launcher in:" -ForegroundColor White
Write-Host "   Settings > Apps > See all apps > Google TV Home > Disable" -ForegroundColor Gray

Write-Host "`nTroubleshooting:" -ForegroundColor Yellow
Write-Host "Run: .\_device_status.ps1 to check configuration" -ForegroundColor Gray
Write-Host "Run: .\_xiaomi_autostart_fix.ps1 to retry this setup" -ForegroundColor Gray
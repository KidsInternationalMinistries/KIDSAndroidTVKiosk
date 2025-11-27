# Reset Launcher to Google TV Home
# Use this script to remove KIDS Kiosk as default launcher and restore Google TV

Write-Host "Reset to Google TV Launcher" -ForegroundColor Cyan
Write-Host "=============================" -ForegroundColor Cyan

# Check device connection
$devices = & ".\platform-tools\adb.exe" devices 2>$null
if ($LASTEXITCODE -ne 0 -or -not ($devices | Select-String "device$")) {
    Write-Host "No device connected." -ForegroundColor Red
    exit 1
}

Write-Host "Device connected" -ForegroundColor Green

# Step 1: Remove HOME role from KIDS Kiosk
Write-Host "`nStep 1: Removing KIDS Kiosk from HOME role..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell cmd role remove-role-holder android.app.role.HOME com.kidsim.tvkiosk 2>$null
Write-Host "HOME role removed from KIDS Kiosk" -ForegroundColor Green

# Step 2: Stop our app to clear it from recent tasks
Write-Host "`nStep 2: Stopping KIDS Kiosk app..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell am force-stop com.kidsim.tvkiosk 2>$null
Write-Host "KIDS Kiosk app stopped" -ForegroundColor Green

# Step 3: Open settings for user to manually select Google TV Launcher
Write-Host "`nStep 3: Opening launcher settings..." -ForegroundColor Yellow
& ".\platform-tools\adb.exe" shell am start -a android.settings.HOME_SETTINGS 2>$null
Write-Host "Home settings opened on device" -ForegroundColor Green

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Manual Steps Required:" -ForegroundColor Yellow
Write-Host "1. On your Android TV screen, select 'Google TV Home' (or similar)" -ForegroundColor White
Write-Host "2. Choose 'Always' to set it as default" -ForegroundColor White
Write-Host "3. Your device will now use the normal Google TV launcher" -ForegroundColor White

Write-Host "`nTo test KIDS Kiosk AutoStart:" -ForegroundColor Cyan
Write-Host "1. Start KIDS Kiosk app" -ForegroundColor White
Write-Host "2. Double-tap back button to open config" -ForegroundColor White
Write-Host "3. Use 'Enable AutoStart' button to test the new functionality" -ForegroundColor White

Write-Host "`nCheck status: .\_device_status.ps1" -ForegroundColor Gray
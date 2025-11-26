# Simplified Auto-Start Debug Script

Write-Host "=== KIOSK AUTO-START DEBUG ===" -ForegroundColor Green

# Install app
Write-Host "Installing app..." -ForegroundColor Yellow
.\platform-tools\adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check device config
Write-Host "Checking device configuration..." -ForegroundColor Yellow
.\platform-tools\adb shell "run-as com.kidsim.tvkiosk cat /data/data/com.kidsim.tvkiosk/shared_prefs/DeviceConfig.xml"

# Set as default launcher
Write-Host "Setting default launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell pm set-home-activity com.kidsim.tvkiosk/.LauncherActivity

# Clear processes
Write-Host "Clearing app..." -ForegroundColor Yellow
.\platform-tools\adb shell am force-stop com.kidsim.tvkiosk

# Test HOME button
Write-Host "Testing HOME button..." -ForegroundColor Yellow
.\platform-tools\adb shell input keyevent KEYCODE_HOME

# Wait and check logs
Write-Host "Checking logs..." -ForegroundColor Yellow
Start-Sleep 3
.\platform-tools\adb logcat -d | Select-String -Pattern "MainActivity|LauncherActivity|DeviceIdManager" | Select-Object -Last 15

Write-Host "=== MANUAL TEST ===" -ForegroundColor Magenta
Write-Host "Now try pressing BACK BACK on TV remote to access config" -ForegroundColor Cyan
Write-Host "Then return to kiosk to see if 'Configuration Updated' appears" -ForegroundColor Cyan
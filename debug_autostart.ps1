# Android TV Kiosk - Auto-Start Configuration Debug Script
# This script helps debug why the app shows a blank screen on auto-start

Write-Host "=== KIOSK APP - AUTO-START CONFIGURATION DEBUG ===" -ForegroundColor Green

# Step 1: Install the updated app
Write-Host "`n1. Installing updated app with auto-start fixes..." -ForegroundColor Yellow
.\platform-tools\adb install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ App installed successfully" -ForegroundColor Green
} else {
    Write-Host "   ❌ App installation failed" -ForegroundColor Red
    exit 1
}

# Step 2: Check current device configuration
Write-Host "`n2. Checking current device configuration..." -ForegroundColor Yellow
Write-Host "   📱 Dumping device SharedPreferences..." -ForegroundColor Cyan
.\platform-tools\adb shell "run-as com.kidsim.tvkiosk cat /data/data/com.kidsim.tvkiosk/shared_prefs/DeviceConfig.xml" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Device configuration found" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Device configuration not found - this may be the issue!" -ForegroundColor Red
}

# Step 3: Set as default launcher
Write-Host "`n3. Setting as default launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell pm set-home-activity com.kidsim.tvkiosk/.LauncherActivity
Write-Host "   ✅ Default launcher set" -ForegroundColor Green

# Step 4: Clear any existing app processes
Write-Host "`n4. Clearing existing app processes..." -ForegroundColor Yellow
.\platform-tools\adb shell am force-stop com.kidsim.tvkiosk
Write-Host "   🔄 App processes cleared" -ForegroundColor Green

# Step 5: Test auto-start with detailed logging
Write-Host "`n5. Testing auto-start with live logging..." -ForegroundColor Yellow
Write-Host "   🎯 Starting fresh logcat monitoring..." -ForegroundColor Cyan

# Clear log buffer and start monitoring in background
.\platform-tools\adb logcat -c
$logcatJob = Start-Job -ScriptBlock {
    param($adbPath)
    & "$adbPath" logcat "*:I" | Where-Object { $_ -match "MainActivity|LauncherActivity|DeviceIdManager|ConfigurationManager" }
} -ArgumentList "$(Get-Location)\platform-tools\adb.exe"

# Wait a moment for logcat to start
Start-Sleep 2

Write-Host "   🚀 Triggering HOME button to test auto-start..." -ForegroundColor Cyan
.\platform-tools\adb shell input keyevent KEYCODE_HOME

# Monitor logs for 10 seconds
Write-Host "   📊 Monitoring logs for 10 seconds..." -ForegroundColor Cyan
Start-Sleep 10

# Get the log output
$logs = Receive-Job $logcatJob -Keep
Stop-Job $logcatJob
Remove-Job $logcatJob

Write-Host "`n=== AUTO-START LOG ANALYSIS ===" -ForegroundColor Magenta

if ($logs) {
    Write-Host "📋 Key log entries:" -ForegroundColor Cyan
    $logs | ForEach-Object { Write-Host "   $_" -ForegroundColor Gray }
    
    # Analyze the logs
    $launcherStarted = $logs | Where-Object { $_ -match "KIOSK LAUNCHER ACTIVITY STARTED" }
    $mainActivityStarted = $logs | Where-Object { $_ -match "MainActivity starting|MainActivity started" }
    $deviceConfigCheck = $logs | Where-Object { $_ -match "Device configuration status|Device ID" }
    $autoStartDetected = $logs | Where-Object { $_ -match "Auto-starting" }
    
    Write-Host "`n🔍 Analysis:" -ForegroundColor Yellow
    
    if ($launcherStarted) {
        Write-Host "   ✅ LauncherActivity started successfully" -ForegroundColor Green
    } else {
        Write-Host "   ❌ LauncherActivity did not start" -ForegroundColor Red
    }
    
    if ($mainActivityStarted) {
        Write-Host "   ✅ MainActivity started successfully" -ForegroundColor Green
    } else {
        Write-Host "   ❌ MainActivity did not start" -ForegroundColor Red
    }
    
    if ($autoStartDetected) {
        Write-Host "   ✅ Auto-start flag detected in MainActivity" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Auto-start flag not detected - check LauncherActivity" -ForegroundColor Yellow
    }
    
    if ($deviceConfigCheck) {
        Write-Host "   ✅ Device configuration check performed" -ForegroundColor Green
        $configStatus = $logs | Where-Object { $_ -match "Device configuration status" }
        if ($configStatus) {
            Write-Host "   📋 Config status: $configStatus" -ForegroundColor Cyan
        }
    } else {
        Write-Host "   ❌ Device configuration check not found" -ForegroundColor Red
    }
} else {
    Write-Host "⚠️  No relevant logs captured. App may not have started." -ForegroundColor Yellow
}

# Step 6: Check current activity
Write-Host "`n6. Checking current foreground activity..." -ForegroundColor Yellow
$currentActivity = .\platform-tools\adb shell dumpsys activity | Select-String -Pattern "mResumedActivity"
if ($currentActivity) {
    Write-Host "   📱 Current activity: $currentActivity" -ForegroundColor Cyan
    if ($currentActivity -match "kidsim") {
        Write-Host "   ✅ Kiosk app is in foreground" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Different app is in foreground" -ForegroundColor Red
    }
} else {
    Write-Host "   ⚠️  Could not determine current activity" -ForegroundColor Yellow
}

# Step 7: Manual test option
Write-Host "`n7. Manual test: Launch configuration screen..." -ForegroundColor Yellow
Write-Host "   🔧 You can now manually test by pressing BACK BACK on your TV remote" -ForegroundColor Cyan
Write-Host "   📋 Then go back into the kiosk to see if 'Configuration Updated' appears" -ForegroundColor Cyan

Write-Host "`n=== SUMMARY & NEXT STEPS ===" -ForegroundColor Magenta

Write-Host "If you see a blank screen on auto-start, the issue is likely:" -ForegroundColor White
Write-Host "  1. Device configuration not properly loaded (check SharedPreferences above)" -ForegroundColor Gray
Write-Host "  2. MainActivity auto-start flag not working (check logs above)" -ForegroundColor Gray  
Write-Host "  3. Network connectivity issues preventing configuration download" -ForegroundColor Gray

Write-Host "`nTo fix the issue:" -ForegroundColor White
Write-Host "  • If device config is missing: Run setup via BACK BACK → configure → return" -ForegroundColor Gray
Write-Host "  • If auto-start flag missing: The LauncherActivity needs to be updated" -ForegroundColor Gray
Write-Host "  • If still blank: Check network connectivity and GitHub config access" -ForegroundColor Gray

Write-Host "`n🎯 The updated app now shows 'Configuration Updated' and handles auto-start better!" -ForegroundColor Green
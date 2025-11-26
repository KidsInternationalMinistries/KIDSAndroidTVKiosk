# Kiosk App - Set Default Launcher Script
# This script ensures the Kiosk app becomes the default launcher on Android TV

Write-Host "=== KIOSK APP - SETTING DEFAULT LAUNCHER ===" -ForegroundColor Green

# Step 1: Set our app as default home activity
Write-Host "1. Setting Kiosk app as default launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell pm set-home-activity com.kidsim.tvkiosk/.LauncherActivity

# Step 2: Trigger HOME intent to activate our launcher  
Write-Host "2. Activating launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# Step 3: Force stop Google TV Launcher to prevent interference
Write-Host "3. Stopping Google TV Launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell am force-stop com.google.android.tvlauncher

# Step 4: Start our launcher activity directly
Write-Host "4. Starting Kiosk launcher..." -ForegroundColor Yellow
.\platform-tools\adb shell am start -n com.kidsim.tvkiosk/.LauncherActivity

# Step 5: Verify our app is running
Write-Host "5. Checking if Kiosk app is active..." -ForegroundColor Yellow
$output = .\platform-tools\adb shell dumpsys activity | Select-String -Pattern "kidsim"
if ($output) {
    Write-Host "SUCCESS: Kiosk app is now active!" -ForegroundColor Green
    Write-Host $output -ForegroundColor Cyan
} else {
    Write-Host "WARNING: Could not verify Kiosk app activation" -ForegroundColor Red
}

Write-Host "`n=== SETUP COMPLETE ===" -ForegroundColor Green
Write-Host "Your Android TV Kiosk app should now:" -ForegroundColor White
Write-Host "- Start automatically when HOME button is pressed" -ForegroundColor Gray  
Write-Host "- Run the WebView with rotation support" -ForegroundColor Gray
Write-Host "- Keep running in the background" -ForegroundColor Gray

Write-Host "`nTo test: Press HOME button on your TV remote" -ForegroundColor Yellow
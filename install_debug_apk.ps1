# Debug APK Auto-Installer Script
# Place this script in your project root and run when needed

Write-Host "🔍 Checking for debug APK on Android TV device..." -ForegroundColor Cyan

# Check if device is connected
$deviceStatus = .\platform-tools\adb devices
if ($deviceStatus -match "device$") {
    Write-Host "✅ Android TV device connected" -ForegroundColor Green
} else {
    Write-Host "❌ No Android TV device found. Please connect device first." -ForegroundColor Red
    exit 1
}

# Check if debug APK exists on device
$apkExists = .\platform-tools\adb shell "test -f /storage/emulated/0/Download/kiosk-debug-test.apk && echo 'exists'"

if ($apkExists -match "exists") {
    Write-Host "✅ Debug APK found on device" -ForegroundColor Green
    
    # Download the APK from device to local machine
    Write-Host "📥 Downloading debug APK from device..." -ForegroundColor Yellow
    .\platform-tools\adb pull "/storage/emulated/0/Download/kiosk-debug-test.apk" "debug-from-device.apk"
    
    # Install it using ADB
    Write-Host "🚀 Installing debug APK..." -ForegroundColor Yellow
    .\platform-tools\adb install -r "debug-from-device.apk"
    
    # Verify installation
    $debugInstalled = .\platform-tools\adb shell pm list packages | Select-String "com.kidsim.tvkiosk.debug"
    if ($debugInstalled) {
        Write-Host "🎉 DEBUG APP SUCCESSFULLY INSTALLED!" -ForegroundColor Green
        Write-Host "You now have both apps:" -ForegroundColor Cyan
        Write-Host "  📱 Production: KIDS TV Kiosk" -ForegroundColor Blue
        Write-Host "  🔧 Debug: KIDS TV Kiosk DEBUG" -ForegroundColor Red
    } else {
        Write-Host "❌ Installation failed" -ForegroundColor Red
    }
    
    # Clean up
    Remove-Item "debug-from-device.apk" -ErrorAction SilentlyContinue
    
} else {
    Write-Host "❌ Debug APK not found on device" -ForegroundColor Red
    Write-Host "💡 First download the debug APK using the main app:" -ForegroundColor Yellow
    Write-Host "   1. Open KIDS TV Kiosk app" -ForegroundColor White
    Write-Host "   2. Go to Update Activity" -ForegroundColor White
    Write-Host "   3. Select 'Debug' and click 'Save and Install'" -ForegroundColor White
    Write-Host "   4. Then run this script again" -ForegroundColor White
}

Write-Host "`nPress any key to exit..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
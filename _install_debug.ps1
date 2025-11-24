# Build and Install Debug Version
# This builds a debug APK with current version numbers and installs it on connected device

Write-Host "Building and installing debug APK..." -ForegroundColor Green

# Check if device is connected
Write-Host "Checking for connected device..." -ForegroundColor Yellow
.\platform-tools\adb devices

$deviceCheck = .\platform-tools\adb devices 2>&1
if ($deviceCheck -match "device$") {
    Write-Host "Device found!" -ForegroundColor Green
} else {
    Write-Host "No device connected! Please connect an Android device and enable USB debugging." -ForegroundColor Red
    exit 1
}

# Clean and build debug
Write-Host "Building debug APK..." -ForegroundColor Yellow
.\gradlew.bat clean assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Debug build completed successfully!" -ForegroundColor Green
    
    # Uninstall existing app first
    Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
    .\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null
    
    # Install the APK
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    Write-Host "Installing APK on device..." -ForegroundColor Yellow
    .\platform-tools\adb install $apkPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Installation completed successfully!" -ForegroundColor Green
        Write-Host "You can now launch the app on your device." -ForegroundColor Yellow
    } else {
        Write-Host "Installation failed!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Debug build failed!" -ForegroundColor Red
    exit 1
}
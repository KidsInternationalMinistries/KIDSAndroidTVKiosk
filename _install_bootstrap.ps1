# Build and Install Bootstrap App
# This builds the KIDS Kiosk Bootstrap app and installs it on the connected device

Write-Host "Building and installing KIDS Kiosk Bootstrap..." -ForegroundColor Green

# Check if device is connected
Write-Host "Checking for connected device..." -ForegroundColor Yellow
$deviceCheck = .\platform-tools\adb devices 2>&1
if ($deviceCheck -match "device$") {
    Write-Host "Device found!" -ForegroundColor Green
} else {
    Write-Host "No device connected! Please connect an Android device and enable USB debugging." -ForegroundColor Red
    exit 1
}

# Build the bootstrap app
Write-Host "Building bootstrap APK..." -ForegroundColor Yellow
.\gradlew.bat clean :updater:assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "Bootstrap build completed successfully!" -ForegroundColor Green
    
    # Uninstall existing bootstrap app first
    Write-Host "Uninstalling existing bootstrap app..." -ForegroundColor Yellow
    .\platform-tools\adb uninstall com.kidsim.updater 2>$null
    
    # Install the APK
    $apkPath = "updater\build\outputs\apk\release\updater-release.apk"
    Write-Host "Installing bootstrap APK on device..." -ForegroundColor Yellow
    .\platform-tools\adb install $apkPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Bootstrap installation completed successfully!" -ForegroundColor Green
        Write-Host "You can now launch 'KIDS Kiosk Bootstrap' on your device." -ForegroundColor Yellow
        
        # Copy to root directory for easier access
        Copy-Item $apkPath "kids-kiosk-bootstrap.apk" -Force
        Write-Host "Also saved as: kids-kiosk-bootstrap.apk" -ForegroundColor Green
    } else {
        Write-Host "Installation failed!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Bootstrap build failed!" -ForegroundColor Red
    exit 1
}
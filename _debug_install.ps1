#!/usr/bin/env pwsh
# Debug Install Script for KIDS Android TV Kiosk
# Builds the app and installs it on attached Android device

Write-Host "=== KIDS Android TV Kiosk - Debug Install ===" -ForegroundColor Green

# Check if adb is available
$adbPath = ".\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    Write-Host "Error: ADB not found at $adbPath" -ForegroundColor Red
    Write-Host "Please ensure platform-tools is in the project directory" -ForegroundColor Yellow
    exit 1
}

# Check for connected devices
Write-Host "Checking for connected devices..." -ForegroundColor Yellow
$devices = & $adbPath devices
$deviceLines = $devices | Select-String "device$"

if ($deviceLines.Count -eq 0) {
    Write-Host "Error: No Android devices connected" -ForegroundColor Red
    Write-Host "Please connect an Android device with USB debugging enabled" -ForegroundColor Yellow
    Write-Host "Available devices:" -ForegroundColor Yellow
    & $adbPath devices
    exit 1
}

Write-Host "Found $($deviceLines.Count) connected device(s)" -ForegroundColor Green

# Build the app
Write-Host "Building the app..." -ForegroundColor Yellow
$buildResult = & .\gradlew.bat assembleDebug 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Build failed" -ForegroundColor Red
    Write-Host $buildResult -ForegroundColor Red
    exit 1
}

Write-Host "Build successful!" -ForegroundColor Green

# Copy APK to root directory
$sourceApk = "app\build\outputs\apk\debug\app-debug.apk"
$targetApk = "app-debug-install.apk"

if (-not (Test-Path $sourceApk)) {
    Write-Host "Error: APK not found at $sourceApk" -ForegroundColor Red
    exit 1
}

Copy-Item $sourceApk $targetApk -Force
Write-Host "APK copied to $targetApk" -ForegroundColor Green

# Install on device
Write-Host "Installing on device..." -ForegroundColor Yellow
$installResult = & $adbPath install -r $targetApk 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Installation failed" -ForegroundColor Red
    Write-Host $installResult -ForegroundColor Red
    exit 1
}

Write-Host "Installation successful!" -ForegroundColor Green

# Clear logcat for fresh logs
Write-Host "Clearing logcat..." -ForegroundColor Yellow
& $adbPath logcat -c

# Optional: Start the app
Write-Host "Starting the main activity..." -ForegroundColor Yellow
& $adbPath shell am start -n com.kidsim.tvkiosk/.MainActivity

Write-Host "=== Debug Install Complete ===" -ForegroundColor Green
Write-Host "App installed and started successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor Cyan
Write-Host "  View logs: .\platform-tools\adb.exe logcat | findstr 'tvkiosk'" -ForegroundColor Gray
Write-Host "  Start main app: .\platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.MainActivity" -ForegroundColor Gray
Write-Host "  Start update app: .\platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.UpdateActivity" -ForegroundColor Gray
Write-Host "  Uninstall: .\platform-tools\adb.exe uninstall com.kidsim.tvkiosk" -ForegroundColor Gray
#!/usr/bin/env pwsh
# Release Install Script for KIDS Android TV Kiosk
# Builds the release app and installs it on attached Android device

Write-Host "=== KIDS Android TV Kiosk - Release Install ===" -ForegroundColor Green

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
Write-Host "Building the release app..." -ForegroundColor Yellow
$buildResult = & .\gradlew.bat assembleRelease 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Build failed" -ForegroundColor Red
    Write-Host $buildResult -ForegroundColor Red
    exit 1
}

Write-Host "Build successful!" -ForegroundColor Green

# Check for release APK (should be signed with debug key for internal distribution)
$sourceApk = "app\build\outputs\apk\release\app-release.apk"
$targetApk = "app-release-install.apk"

if (-not (Test-Path $sourceApk)) {
    Write-Host "Error: Release APK not found at $sourceApk" -ForegroundColor Red
    Write-Host "Expected signed release APK for internal distribution." -ForegroundColor Yellow
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
    Write-Host ""
    Write-Host "Installation failed. Check device connection and USB debugging." -ForegroundColor Yellow
    exit 1
}

Write-Host "Installation successful!" -ForegroundColor Green

# Clear logcat for fresh logs
Write-Host "Clearing logcat..." -ForegroundColor Yellow
& $adbPath logcat -c

# Optional: Start the app
Write-Host "Starting the main activity..." -ForegroundColor Yellow
& $adbPath shell am start -n com.kidsim.tvkiosk/.MainActivity

Write-Host "=== Release Install Complete ===" -ForegroundColor Green
Write-Host "Production app installed and started successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor Cyan
Write-Host "  View logs: .\platform-tools\adb.exe logcat | findstr 'tvkiosk'" -ForegroundColor Gray
Write-Host "  Start main app: .\platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.MainActivity" -ForegroundColor Gray
Write-Host "  Start update app: .\platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.UpdateActivity" -ForegroundColor Gray
Write-Host "  Uninstall: .\platform-tools\adb.exe uninstall com.kidsim.tvkiosk" -ForegroundColor Gray
Write-Host ""
Write-Host "Note: This is the production version with package ID: com.kidsim.tvkiosk" -ForegroundColor Cyan
Write-Host "Built as debuggable release for internal distribution (signed with debug key)" -ForegroundColor Cyan
Write-Host "For debug testing, use: .\_debug_install.ps1 (installs com.kidsim.tvkiosk.debug)" -ForegroundColor Cyan
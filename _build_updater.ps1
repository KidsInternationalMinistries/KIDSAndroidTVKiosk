# Build Updater APK
# This builds the small updater APK that can uninstall and reinstall the main kiosk app

Write-Host "Building updater APK..." -ForegroundColor Green

# Clean and build updater
.\gradlew.bat clean :updater:assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "Updater build completed successfully!" -ForegroundColor Green
    Write-Host "APK Location: updater\build\outputs\apk\release\updater-release.apk" -ForegroundColor Yellow
    
    # Copy to root directory for easier access
    $sourceApk = "updater\build\outputs\apk\release\updater-release.apk"
    $destApk = "kiosk-updater.apk"
    
    if (Test-Path $sourceApk) {
        Copy-Item $sourceApk $destApk -Force
        Write-Host "Copied to: $destApk" -ForegroundColor Green
    }
} else {
    Write-Host "Updater build failed!" -ForegroundColor Red
    exit 1
}
#!/usr/bin/env pwsh
# Compile Debug Script for KIDS Android TV Kiosk
# Only builds the debug APK without installing

Write-Host "=== KIDS Android TV Kiosk - Compile Debug ===" -ForegroundColor Green

# Build the debug APK
Write-Host "Building debug APK..." -ForegroundColor Yellow
& .\gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Build failed" -ForegroundColor Red
    exit 1
}

Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "Debug APK location: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Cyan
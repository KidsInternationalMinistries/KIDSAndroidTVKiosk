# Manual Installation Script for KIDS Android TV Kiosk Apps
# This script helps install both debug and release versions manually

param(
    [Parameter(Mandatory=$true)]
    [string]$DeviceIP,
    
    [switch]$DebugOnly,
    [switch]$ReleaseOnly
)

Write-Host "KIDS Android TV Kiosk - Manual Installation Script" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# Check if ADB is available
try {
    adb version | Out-Null
    Write-Host "✓ ADB is available" -ForegroundColor Green
} catch {
    Write-Host "✗ ADB not found. Please install Android SDK Platform Tools." -ForegroundColor Red
    exit 1
}

# Check if APK files exist
$debugApk = "KIDSTVKiosk-Debug.apk"
$releaseApk = "KIDSTVKiosk-Release.apk"

if (!(Test-Path $debugApk)) {
    Write-Host "✗ Debug APK not found: $debugApk" -ForegroundColor Red
    exit 1
}

if (!(Test-Path $releaseApk)) {
    Write-Host "✗ Release APK not found: $releaseApk" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Both APK files found" -ForegroundColor Green

# Connect to device
Write-Host "`nConnecting to device at $DeviceIP..." -ForegroundColor Yellow
$connectResult = adb connect "${DeviceIP}:5555"
Write-Host $connectResult

# Check connection
$devices = adb devices
if ($devices -match $DeviceIP) {
    Write-Host "✓ Connected to device" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to connect to device" -ForegroundColor Red
    exit 1
}

# Install apps based on parameters
if (-not $ReleaseOnly) {
    Write-Host "`nInstalling Debug APK..." -ForegroundColor Yellow
    $debugResult = adb install -r $debugApk
    if ($debugResult -match "Success") {
        Write-Host "✓ Debug app installed successfully" -ForegroundColor Green
    } else {
        Write-Host "✗ Debug app installation failed: $debugResult" -ForegroundColor Red
    }
}

if (-not $DebugOnly) {
    Write-Host "`nInstalling Release APK..." -ForegroundColor Yellow
    $releaseResult = adb install -r $releaseApk
    if ($releaseResult -match "Success") {
        Write-Host "✓ Release app installed successfully" -ForegroundColor Green
    } else {
        Write-Host "✗ Release app installation failed: $releaseResult" -ForegroundColor Red
    }
}

Write-Host "`n=================================================" -ForegroundColor Green
Write-Host "Installation Summary:" -ForegroundColor Green
Write-Host "- Debug App (red theme): com.kidsim.tvkiosk.debug" -ForegroundColor Cyan
Write-Host "- Release App (blue theme): com.kidsim.tvkiosk" -ForegroundColor Cyan
Write-Host "- Both apps can coexist and update independently" -ForegroundColor Cyan
Write-Host "- Debug app updates to debug releases only" -ForegroundColor Cyan
Write-Host "- Release app updates to production releases only" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Green

Write-Host "`nTo install in the future:" -ForegroundColor Yellow
Write-Host "  Both apps: .\manual_install.ps1 -DeviceIP 192.168.1.100" -ForegroundColor White
Write-Host "  Debug only: .\manual_install.ps1 -DeviceIP 192.168.1.100 -DebugOnly" -ForegroundColor White
Write-Host "  Release only: .\manual_install.ps1 -DeviceIP 192.168.1.100 -ReleaseOnly" -ForegroundColor White
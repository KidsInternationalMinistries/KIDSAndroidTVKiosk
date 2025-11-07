# Android TV Kiosk Build Script - Enhanced Version
# PowerShell script to build the Android APK with enhanced features

param(
    [switch]$Clean,
    [switch]$Release,
    [switch]$Help,
    [switch]$ConfigSample
)

function Show-Help {
    Write-Host "Android TV Kiosk Build Script - Enhanced Version" -ForegroundColor Green
    Write-Host "Usage: .\build.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Options:" -ForegroundColor Cyan
    Write-Host "  -Clean        Clean the project before building"
    Write-Host "  -Release      Build release APK (default is debug)"
    Write-Host "  -ConfigSample Show sample configuration file"
    Write-Host "  -Help         Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Cyan
    Write-Host "  .\build.ps1                    # Build debug APK"
    Write-Host "  .\build.ps1 -Clean             # Clean and build debug APK"
    Write-Host "  .\build.ps1 -Release           # Build release APK"
    Write-Host "  .\build.ps1 -Clean -Release    # Clean and build release APK"
    Write-Host "  .\build.ps1 -ConfigSample      # Show sample configuration"
    Write-Host ""
    Write-Host "Enhanced Features:" -ForegroundColor Magenta
    Write-Host "  * GitHub-based configuration management"
    Write-Host "  * Multi-page rotation with configurable timing"
    Write-Host "  * Auto-start on device boot"
    Write-Host "  * Dynamic orientation support"
    Write-Host "  * Error handling with retry functionality"
    Write-Host "  * Memory management and watchdog service"
}

function Test-JavaInstallation {
    Write-Host "Checking Java installation..." -ForegroundColor Yellow
    try {
        $null = java -version 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "checkmark Java is installed" -ForegroundColor Green
            return $true
        } else {
            Write-Host "x Java is not installed or not in PATH" -ForegroundColor Red
            Write-Host "Please install Java JDK 8 or higher" -ForegroundColor Yellow
            return $false
        }
    }
    catch {
        Write-Host "x Java is not installed or not in PATH" -ForegroundColor Red
        Write-Host "Please install Java JDK 8 or higher" -ForegroundColor Yellow
        return $false
    }
}

function Test-AndroidSdk {
    Write-Host "Checking Android SDK configuration..." -ForegroundColor Yellow
    if ($env:ANDROID_HOME) {
        Write-Host "checkmark ANDROID_HOME is set: $env:ANDROID_HOME" -ForegroundColor Green
        return $true
    } else {
        Write-Host "warning ANDROID_HOME is not set" -ForegroundColor Yellow
        Write-Host "You may need to set ANDROID_HOME environment variable" -ForegroundColor Yellow
        Write-Host "Example: C:\Users\$env:USERNAME\AppData\Local\Android\Sdk" -ForegroundColor Gray
        return $false
    }
}

function Start-Build {
    param([string]$BuildType, [bool]$CleanFirst)
    
    Write-Host "`nBuilding Android TV Kiosk APK..." -ForegroundColor Green
    Write-Host "Build Type: $BuildType" -ForegroundColor Cyan
    
    # Clean if requested
    if ($CleanFirst) {
        Write-Host "`nCleaning project..." -ForegroundColor Yellow
        & .\gradlew.bat clean
        if ($LASTEXITCODE -ne 0) {
            Write-Host "x Clean failed!" -ForegroundColor Red
            return $false
        }
        Write-Host "checkmark Project cleaned successfully" -ForegroundColor Green
    }
    
    # Build the APK
    Write-Host "`nBuilding $BuildType APK..." -ForegroundColor Yellow
    $buildCommand = if ($BuildType -eq "release") { "assembleRelease" } else { "assembleDebug" }
    
    & .\gradlew.bat $buildCommand
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "checkmark Build completed successfully!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "x Build failed!" -ForegroundColor Red
        Write-Host "Make sure you have Android SDK installed and configured." -ForegroundColor Yellow
        return $false
    }
}

function Show-BuildResults {
    param([string]$BuildType)
    
    $apkPath = if ($BuildType -eq "release") {
        "app\build\outputs\apk\release\app-release-unsigned.apk"
    } else {
        "app\build\outputs\apk\debug\app-debug.apk"
    }
    
    Write-Host "`n============================================================" -ForegroundColor Cyan
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Cyan
    
    Write-Host "`nAPK Location:" -ForegroundColor Yellow
    Write-Host $apkPath -ForegroundColor White
    
    if (Test-Path $apkPath) {
        $fileInfo = Get-Item $apkPath
        Write-Host "`nAPK Details:" -ForegroundColor Yellow
        Write-Host "Size: $([math]::Round($fileInfo.Length / 1MB, 2)) MB" -ForegroundColor White
        Write-Host "Created: $($fileInfo.CreationTime)" -ForegroundColor White
    }
    
    Write-Host "`nInstallation Instructions:" -ForegroundColor Yellow
    Write-Host "1. Enable Developer Options on your Android TV" -ForegroundColor White
    Write-Host "2. Enable USB Debugging" -ForegroundColor White
    Write-Host "3. Connect Android TV to your computer" -ForegroundColor White
    Write-Host "4. Run: adb install `"$apkPath`"" -ForegroundColor Cyan
    
    Write-Host "`nAlternative Installation:" -ForegroundColor Yellow
    Write-Host "- Copy APK to USB drive and install via file manager" -ForegroundColor White
    Write-Host "- Use Android TV's 'Install from Unknown Sources' option" -ForegroundColor White
    
    Write-Host "`nApp Features:" -ForegroundColor Yellow
    Write-Host "- Displays sponsor.kidsim.org in fullscreen kiosk mode" -ForegroundColor White
    Write-Host "- Auto-refreshes every hour" -ForegroundColor White
    Write-Host "- Optimized for Android TV 11" -ForegroundColor White
    
    Write-Host "`n============================================================" -ForegroundColor Cyan
}

# Main script execution
Clear-Host
Write-Host "Android TV Kiosk Builder" -ForegroundColor Green -BackgroundColor Black
Write-Host "========================" -ForegroundColor Green

if ($Help) {
    Show-Help
    exit 0
}

if ($ConfigSample) {
    Write-Host "Sample Configuration File (config.json):" -ForegroundColor Green
    Write-Host ""
    if (Test-Path "config-sample.json") {
        Get-Content "config-sample.json" | Write-Host -ForegroundColor Cyan
    } else {
        Write-Host "config-sample.json not found in current directory" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Configuration Instructions:" -ForegroundColor Yellow
    Write-Host "1. Create a GitHub repository for your kiosk configuration"
    Write-Host "2. Upload a config.json file with your device settings"
    Write-Host "3. Update the URL in ConfigurationManager.java"
    Write-Host "4. Build and deploy the app"
    Write-Host ""
    exit 0
}

# Check prerequisites
$javaOk = Test-JavaInstallation
$androidOk = Test-AndroidSdk

if (-not $javaOk) {
    Write-Host "`nx Cannot proceed without Java installation" -ForegroundColor Red
    exit 1
}

# Determine build type
$buildType = if ($Release) { "release" } else { "debug" }

# Start the build process
Write-Host "`nStarting build process..." -ForegroundColor Cyan
$buildSuccess = Start-Build -BuildType $buildType -CleanFirst $Clean

if ($buildSuccess) {
    Show-BuildResults -BuildType $buildType
} else {
    Write-Host "`nx Build process failed" -ForegroundColor Red
    Write-Host "Check the error messages above for details" -ForegroundColor Yellow
    exit 1
}


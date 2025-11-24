# Install Latest APK (from GitHub latest release or local debug)
# This downloads and installs the latest release APK from GitHub, or falls back to local debug APK

param(
    [switch]$local,
    [switch]$force
)

Write-Host "Installing latest APK..." -ForegroundColor Green

if ($local) {
    # Install local debug APK
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    
    if (-not (Test-Path $apkPath)) {
        Write-Host "Local debug APK not found. Run _build_debug.ps1 first." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Installing local debug APK..." -ForegroundColor Yellow
    
    # Uninstall existing app first
    Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
    .\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null
    
    .\platform-tools\adb install $apkPath
} else {
    # Download and install latest GitHub release
    Write-Host "Fetching latest release from GitHub..." -ForegroundColor Yellow
    
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/sbondCo/KIDSAndroidTVKiosk/releases/latest"
        $apkAsset = $release.assets | Where-Object { $_.name -like "*kidsandroidtvkiosk*.apk" }
        
        if (-not $apkAsset) {
            Write-Host "No APK found in latest release" -ForegroundColor Red
            exit 1
        }
        
        $downloadUrl = $apkAsset.browser_download_url
        $fileName = $apkAsset.name
        $localPath = "temp_$fileName"
        
        Write-Host "Downloading $fileName..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $downloadUrl -OutFile $localPath
        
        Write-Host "Installing downloaded APK..." -ForegroundColor Yellow
        
        # Uninstall existing app first
        Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
        .\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null
        
        .\platform-tools\adb install $localPath
        
        # Clean up downloaded file
        Remove-Item $localPath -Force
        
    } catch {
        Write-Host "Failed to download or install from GitHub: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Falling back to local debug APK..." -ForegroundColor Yellow
        
        $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
        if (Test-Path $apkPath) {
            Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
            .\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null
            
            .\platform-tools\adb install $apkPath
        } else {
            Write-Host "No local debug APK available either. Run _build_debug.ps1 first." -ForegroundColor Red
            exit 1
        }
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "Installation completed successfully!" -ForegroundColor Green
} else {
    Write-Host "Installation failed!" -ForegroundColor Red
    exit 1
}
# Install Prerelease APK
# This downloads and installs the current prerelease APK from GitHub

Write-Host "Installing prerelease APK..." -ForegroundColor Green

# Check if device is connected
Write-Host "Checking for connected device..." -ForegroundColor Yellow
$deviceCheck = .\platform-tools\adb devices 2>&1
if ($deviceCheck -match "device$") {
    Write-Host "Device found!" -ForegroundColor Green
} else {
    Write-Host "No device connected! Please connect an Android device and enable USB debugging." -ForegroundColor Red
    exit 1
}

# Find GitHub CLI executable
$ghCommand = $null
$ghPaths = @(
    "gh",  # Try PATH first
    "C:\Program Files\GitHub CLI\gh.exe",
    "C:\Program Files (x86)\GitHub CLI\gh.exe",
    "$env:LOCALAPPDATA\GitHubCLI\gh.exe"
)

foreach ($path in $ghPaths) {
    if ($path -eq "gh") {
        if (Get-Command "gh" -ErrorAction SilentlyContinue) {
            $ghCommand = "gh"
            break
        }
    } elseif (Test-Path $path) {
        $ghCommand = $path
        break
    }
}

if (-not $ghCommand) {
    Write-Host "GitHub CLI (gh) is not installed" -ForegroundColor Red
    Write-Host "Please install GitHub CLI:" -ForegroundColor Yellow
    Write-Host "  winget install GitHub.cli" -ForegroundColor Yellow
    Write-Host "Or download manually from: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/prerelease" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using GitHub CLI: $ghCommand" -ForegroundColor Green

# Check if logged into GitHub CLI
& $ghCommand auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Not logged into GitHub CLI" -ForegroundColor Red
    Write-Host "Please run: $ghCommand auth login" -ForegroundColor Yellow
    Write-Host "Or download manually from: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/prerelease" -ForegroundColor Yellow
    exit 1
}

# Download and install prerelease
Write-Host "Downloading prerelease APK..." -ForegroundColor Yellow

try {
    # Get prerelease info
    $releases = & $ghCommand release list --exclude-drafts --json tagName,isPrerelease,name | ConvertFrom-Json
    $prerelease = $releases | Where-Object { $_.tagName -eq "prerelease" -and $_.isPrerelease -eq $true } | Select-Object -First 1
    
    if (-not $prerelease) {
        Write-Host "No prerelease found with tag 'prerelease'" -ForegroundColor Red
        Write-Host "Check releases at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host "Found prerelease: $($prerelease.name)" -ForegroundColor Green
    
    # Download prerelease assets to temp directory
    $tempDir = "temp_prerelease_install"
    if (Test-Path $tempDir) {
        Remove-Item -Path $tempDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    
    & $ghCommand release download prerelease --dir $tempDir
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to download prerelease" -ForegroundColor Red
        exit 1
    }
    
    # Find APK file
    $apkFiles = Get-ChildItem -Path $tempDir -Filter "*.apk"
    
    if ($apkFiles.Count -eq 0) {
        Write-Host "No APK file found in prerelease" -ForegroundColor Red
        Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        exit 1
    }
    
    if ($apkFiles.Count -gt 1) {
        Write-Host "Multiple APK files found, using first one: $($apkFiles[0].Name)" -ForegroundColor Yellow
    }
    
    $apkPath = $apkFiles[0].FullName
    Write-Host "Installing APK: $($apkFiles[0].Name)" -ForegroundColor Yellow
    
    # Uninstall existing app first
    Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
    .\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null
    
    # Install the APK
    .\platform-tools\adb install $apkPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Prerelease installation completed successfully!" -ForegroundColor Green
        Write-Host "You can now launch the app on your device." -ForegroundColor Yellow
    } else {
        Write-Host "Installation failed!" -ForegroundColor Red
        exit 1
    }
    
    # Clean up temp directory
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    
} catch {
    Write-Host "Failed to download or install prerelease: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "You can download manually from: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/prerelease" -ForegroundColor Yellow
    
    # Clean up temp directory
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}
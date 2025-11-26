# Install Debug APK
# This compiles the current code and installs the APK on connected device (no GitHub publishing)

Write-Host "Building and installing debug APK..." -ForegroundColor Green

# Check if device is connected
Write-Host "Checking for connected device..." -ForegroundColor Yellow
$deviceCheck = .\platform-tools\adb devices 2>&1
if ($deviceCheck -match "device$") {
    Write-Host "Device found!" -ForegroundColor Green
} else {
    Write-Host "No device connected! Please connect an Android device and enable USB debugging." -ForegroundColor Red
    exit 1
}

# Auto-increment version code for local debug build
Write-Host "Auto-incrementing version code for debug build..." -ForegroundColor Yellow

# Read current version from build.gradle
$buildGradlePath = "app\build.gradle"
$buildGradleContent = Get-Content $buildGradlePath -Raw

# Extract current version code
if ($buildGradleContent -match 'versionCode\s+(\d+)') {
    $currentVersionCode = [int]$matches[1]
    $newVersionCode = $currentVersionCode + 1
    
    # Update version code in build.gradle
    $buildGradleContent = $buildGradleContent -replace "versionCode\s+\d+", "versionCode $newVersionCode"
    Set-Content -Path $buildGradlePath -Value $buildGradleContent -NoNewline
    
    Write-Host "Version code updated: $currentVersionCode -> $newVersionCode" -ForegroundColor Green
} else {
    Write-Host "Could not find version code in build.gradle" -ForegroundColor Red
    exit 1
}

# Get version name for display
if ($buildGradleContent -match 'versionName\s+"([^"]+)"') {
    $versionName = $matches[1]
    Write-Host "Version name: $versionName" -ForegroundColor Green
} else {
    $versionName = "Unknown"
}

# Check if changes need to be committed
$gitStatus = git status --porcelain 2>$null
if ($gitStatus) {
    # Commit the version code update
    Write-Host "Committing version code update..." -ForegroundColor Yellow
    $currentBranch = git rev-parse --abbrev-ref HEAD 2>$null
    if (-not $currentBranch) {
        Write-Host "Not in a git repository or no branch detected" -ForegroundColor Red
        exit 1
    }
    
    git add app/build.gradle
    $commitMessage = "Debug build $newVersionCode - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    git commit -m $commitMessage
    Write-Host "Changes committed to $currentBranch branch" -ForegroundColor Green
}

# Clean and build release APK
Write-Host "Building release APK..." -ForegroundColor Yellow
.\gradlew clean assembleRelease

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Release APK built successfully" -ForegroundColor Green

# Find the built APK
$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at expected location: $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host "Installing APK: $apkPath" -ForegroundColor Yellow

# Uninstall existing app first
Write-Host "Uninstalling existing app..." -ForegroundColor Yellow
.\platform-tools\adb uninstall com.kidsim.tvkiosk 2>$null

# Install the APK
.\platform-tools\adb install $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "" -ForegroundColor Green
    Write-Host "=== Debug Installation Complete ===" -ForegroundColor Green
    Write-Host "Version: $versionName (build $newVersionCode)" -ForegroundColor Green
    Write-Host "APK installed successfully!" -ForegroundColor Green
    Write-Host "You can now launch the app on your device." -ForegroundColor Yellow
    Write-Host "" -ForegroundColor Green
} else {
    Write-Host "Installation failed!" -ForegroundColor Red
    exit 1
}
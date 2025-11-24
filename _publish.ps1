# ===================================================================
# KIDS Android TV Kiosk - Publish Prerelease
# ===================================================================
# This script builds and publishes prerelease builds to GitHub
# Usage: 
#   ._publish_prerelease.ps1 -Message "Feature update"
#   ._publish_prerelease.ps1  (will prompt for message)
# ===================================================================

param(
    [string]$Message = ""
)

Write-Host "=== KIDS Android TV Kiosk - Publish Prerelease ===" -ForegroundColor Green
Write-Host ""

# Check current branch
$currentBranch = git branch --show-current
Write-Host "Publishing prerelease from branch: $currentBranch" -ForegroundColor Cyan

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
    Write-Host "Error: GitHub CLI (gh) is not installed" -ForegroundColor Red
    Write-Host "Please install GitHub CLI from: https://cli.github.com/" -ForegroundColor Yellow
    Write-Host "Or use: winget install --id GitHub.cli" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using GitHub CLI: $ghCommand" -ForegroundColor Green

# Check if logged into GitHub CLI
$ghStatus = & $ghCommand auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Not logged into GitHub CLI" -ForegroundColor Red
    Write-Host "Please run: $ghCommand auth login" -ForegroundColor Yellow
    exit 1
}

# Auto-increment version code for prerelease builds
Write-Host "Auto-incrementing version code for prerelease build..." -ForegroundColor Cyan

# Read current version from build.gradle
$buildGradlePath = "app\build.gradle"
if (-not (Test-Path $buildGradlePath)) {
    Write-Host "Error: build.gradle not found" -ForegroundColor Red
    exit 1
}

$buildGradleContent = Get-Content $buildGradlePath -Raw

# Extract current version code and version name
if ($buildGradleContent -match 'versionCode\s+(\d+)') {
    $currentVersionCode = [int]$matches[1]
    $newVersionCode = $currentVersionCode + 1
} else {
    Write-Host "Error: Could not find versionCode in build.gradle" -ForegroundColor Red
    exit 1
}

if ($buildGradleContent -match 'versionName\s+"([^"]+)"') {
    $baseVersionName = $matches[1]
} else {
    Write-Host "Error: Could not find versionName in build.gradle" -ForegroundColor Red
    exit 1
}

# Update version code in build.gradle (keep version name as-is for prerelease)
$updatedContent = $buildGradleContent -replace 'versionCode\s+\d+', "versionCode $newVersionCode"
Set-Content -Path $buildGradlePath -Value $updatedContent -NoNewline

Write-Host "Version code updated: $currentVersionCode -> $newVersionCode" -ForegroundColor Green
Write-Host "Version name: $baseVersionName" -ForegroundColor Green

# Configure prerelease build variables
$buildTask = "assembleRelease"
$apkPath = "app/build/outputs/apk/release/app-release.apk"
$releaseTag = "prerelease"
$releaseTitle = "$baseVersionName-build$newVersionCode (PreRelease)"
$releaseNotes = "Prerelease build $newVersionCode for testing"
$isPrerelease = $true

# Get commit message
if ([string]::IsNullOrEmpty($Message)) {
    $defaultMessage = "Prerelease update build$newVersionCode - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    $Message = Read-Host "Enter commit message (or press Enter for default: '$defaultMessage')"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = $defaultMessage
    }
}

Write-Host "Publishing prerelease build with message: '$Message'" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean any build files that shouldn't be committed
Write-Host "Step 1: Cleaning build files..." -ForegroundColor Yellow
git clean -fd .gradle/ app/build/ build/ 2>$null
Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "app/build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "build" -ErrorAction SilentlyContinue

# Step 2: Add and commit all changes
Write-Host "Step 2: Committing changes..." -ForegroundColor Yellow
git add .
$statusCheck = git status --porcelain
if ($statusCheck) {
    Write-Host "Files to be committed:" -ForegroundColor Cyan
    git status --porcelain
    Write-Host ""
    
    git commit -m "$Message"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to commit changes" -ForegroundColor Red
        exit 1
    }
    Write-Host "Changes committed to $currentBranch branch" -ForegroundColor Green
} else {
    Write-Host "No changes to commit" -ForegroundColor Yellow
}

# Step 3: Build release APK
Write-Host "Step 3: Building release APK..." -ForegroundColor Yellow
# Clean first to ensure version changes are picked up
& .\gradlew.bat clean $buildTask
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build release APK" -ForegroundColor Red
    exit 1
}

# Check if APK exists
if (-not (Test-Path $apkPath)) {
    Write-Host "Error: Release APK not found at $apkPath" -ForegroundColor Red
    exit 1
}
Write-Host "Release APK built successfully" -ForegroundColor Green

# Step 4: Push to GitHub
Write-Host "Step 4: Pushing to GitHub..." -ForegroundColor Yellow
git push origin $currentBranch
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red
    exit 1
}

# Step 5: Create/update GitHub prerelease
Write-Host "Step 5: Creating/updating GitHub prerelease..." -ForegroundColor Yellow

# Check if prerelease already exists and delete it
$existingRelease = & $ghCommand release view "prerelease" 2>$null
if ($existingRelease) {
    Write-Host "Updating existing prerelease..." -ForegroundColor Cyan
    & $ghCommand release delete "prerelease" --yes
}
Write-Host "Creating new prerelease..." -ForegroundColor Cyan

# Create prerelease APK with proper naming
$prereleaseApkName = "kidsandroidtvkiosk-$baseVersionName-build$newVersionCode-prerelease.apk"
Copy-Item $apkPath $prereleaseApkName

# Create GitHub prerelease
& $ghCommand release create $releaseTag $prereleaseApkName --title $releaseTitle --notes $releaseNotes --prerelease --target $currentBranch
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create GitHub prerelease" -ForegroundColor Red
    exit 1
}

# Clean up the copied APK file
Remove-Item $prereleaseApkName -Force

# Success summary
Write-Host ""
Write-Host "=== Prerelease Publish Complete ===" -ForegroundColor Green
Write-Host "Changes committed to $currentBranch branch" -ForegroundColor Green
Write-Host "Release APK built successfully" -ForegroundColor Green
Write-Host "Pushed to GitHub" -ForegroundColor Green
Write-Host "Prerelease created/updated" -ForegroundColor Green
Write-Host ""
Write-Host "Prerelease available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/prerelease" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Test the prerelease on devices" -ForegroundColor White
Write-Host "- When ready for production, run: .\_promote_prerelease.ps1" -ForegroundColor White
Write-Host ""
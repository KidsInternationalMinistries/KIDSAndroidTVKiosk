#!/usr/bin/env pwsh
#
# bump_version.ps1 - Simple version bumping for Android TV Kiosk
# 
# This script:
# 1. Shows current version from build.gradle
# 2. Prompts for new version number
# 3. Updates versionName and auto-increments versionCode
# 4. Creates git branch and commits changes
# 5. Ready for testing and publishing
#

param(
    [string]$NewVersion = "",
    [switch]$Help
)

if ($Help) {
    Write-Host "bump_version.ps1 - Bump version for Android TV Kiosk" -ForegroundColor Green
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\bump_version.ps1                  # Interactive mode"
    Write-Host "  .\bump_version.ps1 -NewVersion 1.1  # Direct version"
    Write-Host ""
    Write-Host "This script will:"
    Write-Host "  - Show current version from build.gradle"
    Write-Host "  - Update versionName and increment versionCode"
    Write-Host "  - Create git branch: version/vX.X"
    Write-Host "  - Commit changes automatically"
    exit 0
}

# Color functions
function Write-Success { param($Message) Write-Host $Message -ForegroundColor Green }
function Write-Info { param($Message) Write-Host $Message -ForegroundColor Cyan }
function Write-Warning { param($Message) Write-Host $Message -ForegroundColor Yellow }
function Write-Error { param($Message) Write-Host $Message -ForegroundColor Red }

Write-Info "Android TV Kiosk Version Bump Tool"
Write-Info "==================================="

# Check if we're in the right directory
$buildGradlePath = "app\build.gradle"
if (-not (Test-Path $buildGradlePath)) {
    Write-Error "Error: build.gradle not found. Run this from the project root."
    exit 1
}

# Read current version from build.gradle
Write-Info "Reading current version..."
$buildGradleContent = Get-Content $buildGradlePath -Raw

# Extract current version info using regex
if ($buildGradleContent -match 'versionCode\s+(\d+)') {
    $currentVersionCode = [int]$matches[1]
} else {
    Write-Error "Could not find versionCode in build.gradle"
    exit 1
}

if ($buildGradleContent -match 'versionName\s+"([^"]+)"') {
    $currentVersionName = $matches[1]
} else {
    Write-Error "Could not find versionName in build.gradle"
    exit 1
}

Write-Success "Current Version: v$currentVersionName (code: $currentVersionCode)"

# Get new version number
if (-not $NewVersion) {
    Write-Info ""
    Write-Host "Enter new version number (e.g., 1.1, 2.0, 1.0.1): " -NoNewline -ForegroundColor Yellow
    $NewVersion = Read-Host
}

# Validate version format
if (-not $NewVersion -or $NewVersion -eq $currentVersionName) {
    Write-Warning "No version change needed. Exiting."
    exit 0
}

if ($NewVersion -notmatch '^\d+\.\d+(\.\d+)?$') {
    Write-Error "Invalid version format. Use format like: 1.0, 1.1, 2.0, 1.0.1"
    exit 1
}

$newVersionCode = $currentVersionCode + 1

Write-Info ""
Write-Info "Version Update Plan:"
Write-Host "  Version Name: $currentVersionName -> $NewVersion" -ForegroundColor White
Write-Host "  Version Code: $currentVersionCode -> $newVersionCode" -ForegroundColor White

Write-Host ""
Write-Host "Continue with version bump? (y/N): " -NoNewline -ForegroundColor Yellow
$confirm = Read-Host
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Warning "Version bump cancelled."
    exit 0
}

# Update build.gradle
Write-Info ""
Write-Info "Updating build.gradle..."

$updatedContent = $buildGradleContent -replace 'versionCode\s+\d+', "versionCode $newVersionCode"
$updatedContent = $updatedContent -replace 'versionName\s+"[^"]+"', "versionName `"$NewVersion`""

Set-Content -Path $buildGradlePath -Value $updatedContent -NoNewline

Write-Success "Updated build.gradle"

# Git operations
Write-Info ""
Write-Info "Creating git branch and committing..."

$branchName = "version/v$NewVersion"
$commitMessage = "Bump version to v$NewVersion (code: $newVersionCode)"

try {
    # Check if we have uncommitted changes
    $gitStatus = git status --porcelain 2>$null
    if ($gitStatus -and $gitStatus.Count -gt 1) {
        Write-Warning "You have uncommitted changes. Stashing them first..."
        git stash push -m "Auto-stash before version bump" 2>$null
    }

    # Create and switch to new branch
    $currentBranch = git branch --show-current 2>$null
    Write-Info "Current branch: $currentBranch"
    
    git checkout -b $branchName 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create branch: $branchName"
        exit 1
    }
    Write-Success "Created branch: $branchName"

    # Stage and commit the version change
    git add $buildGradlePath 2>$null
    git commit -m $commitMessage 2>$null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Committed version bump"
    } else {
        Write-Error "Failed to commit changes"
        exit 1
    }

} catch {
    Write-Error "Git operation failed: $($_.Exception.Message)"
    exit 1
}

# Success summary
Write-Info ""
Write-Success "Version Bump Complete!"
Write-Info "======================"
Write-Host "New Version: v$NewVersion (code: $newVersionCode)" -ForegroundColor White
Write-Host "Branch: $branchName" -ForegroundColor White
Write-Host "Changes committed and ready" -ForegroundColor White

Write-Info ""
Write-Info "Next Steps:"
Write-Host "  1. Test the new version:" -ForegroundColor White
Write-Host "     .\_build_install_publish\debug_install.ps1" -ForegroundColor Cyan
Write-Host ""
Write-Host "  2. Publish to test branch:" -ForegroundColor White
Write-Host "     .\_build_install_publish\git_publish_debug.ps1 -Message `"Version v$NewVersion testing`"" -ForegroundColor Cyan
Write-Host ""
Write-Host "  3. Create production release:" -ForegroundColor White
Write-Host "     .\_build_install_publish\git_publish_release.ps1 -Version `"v$NewVersion`"" -ForegroundColor Cyan

Write-Info ""
Write-Success "Ready to test and deploy v$NewVersion!"
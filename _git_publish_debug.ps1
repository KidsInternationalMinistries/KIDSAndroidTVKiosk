# ===================================================================
# KIDS Android TV Kiosk - Git Publish Debug
# ===================================================================
# This script builds and publishes a debug release to GitHub
# Creates/updates a "debug" pre-release with the latest debug APK
# ===================================================================

param(
    [string]$Message = ""
)

Write-Host "=== KIDS Android TV Kiosk - Git Publish Debug ===" -ForegroundColor Green
Write-Host ""

# Check if we're on test branch
$currentBranch = git branch --show-current
if ($currentBranch -ne "test") {
    Write-Host "Error: You must be on the 'test' branch to publish debug" -ForegroundColor Red
    Write-Host "Current branch: $currentBranch" -ForegroundColor Yellow
    Write-Host "Run: git checkout test" -ForegroundColor Yellow
    exit 1
}

# Check if GitHub CLI is available
if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {
    Write-Host "Error: GitHub CLI (gh) is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install GitHub CLI from: https://cli.github.com/" -ForegroundColor Yellow
    Write-Host "Or use: winget install --id GitHub.cli" -ForegroundColor Yellow
    exit 1
}

# Check if logged into GitHub CLI
$ghStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Not logged into GitHub CLI" -ForegroundColor Red
    Write-Host "Please run: gh auth login" -ForegroundColor Yellow
    exit 1
}

# Get commit message
if ([string]::IsNullOrEmpty($Message)) {
    $Message = Read-Host "Enter commit message (or press Enter for default)"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = "Debug update - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }
}

Write-Host "Publishing to test branch with message: '$Message'" -ForegroundColor Cyan
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
    Write-Host "Changes committed to test branch" -ForegroundColor Green
} else {
    Write-Host "No changes to commit" -ForegroundColor Yellow
}

# Step 3: Build debug APK
Write-Host "Step 3: Building debug APK..." -ForegroundColor Yellow
.\gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build debug APK" -ForegroundColor Red
    exit 1
}

# Check if debug APK exists
$debugApk = "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path $debugApk)) {
    Write-Host "Error: Debug APK not found at $debugApk" -ForegroundColor Red
    exit 1
}
Write-Host "Debug APK built successfully" -ForegroundColor Green

# Step 4: Push to GitHub
Write-Host "Step 4: Pushing to GitHub..." -ForegroundColor Yellow
git push origin test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red
    exit 1
}

# Step 5: Create/update debug release
Write-Host "Step 5: Creating/updating debug release..." -ForegroundColor Yellow

# Check if debug release already exists
$existingRelease = gh release view "debug" 2>$null
if ($existingRelease) {
    Write-Host "Updating existing debug release..." -ForegroundColor Cyan
    # Delete existing release
    gh release delete "debug" --yes
}

# Create new debug release
Write-Host "Creating new debug pre-release..." -ForegroundColor Cyan
gh release create "debug" $debugApk `
    --title "Debug Build" `
    --notes "Latest debug build for testing. This is automatically updated with each debug publish." `
    --prerelease `
    --target test

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Debug Publish Complete ===" -ForegroundColor Green
Write-Host "✓ Changes committed to test branch" -ForegroundColor Green
Write-Host "✓ Debug APK built successfully" -ForegroundColor Green
Write-Host "✓ Pushed to GitHub" -ForegroundColor Green
Write-Host "✓ Debug pre-release created/updated" -ForegroundColor Green
Write-Host ""
Write-Host "Debug release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/debug" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Test the debug release on devices" -ForegroundColor White
Write-Host "- When ready for production, run: .\_git_publish_release.ps1" -ForegroundColor White
Write-Host ""

# Clean up build files and add only source code
Write-Host "Step 1: Cleaning build files..." -ForegroundColor Yellow
git clean -fd .gradle/ app/build/ build/ 2>$null
git reset HEAD . 2>$null

Write-Host "Step 2: Adding source code changes..." -ForegroundColor Yellow
# Add only source files, not build files
git add .gitignore
git add *.md
git add *.json
git add *.gradle
git add *.properties
git add *.bat
git add *.ps1
git add gradlew*
git add "app/src/"
git add "gradle/"

# Show what we're about to commit
Write-Host ""
Write-Host "Files to be committed:" -ForegroundColor Cyan
git diff --cached --name-only | ForEach-Object { Write-Host "  + $_" -ForegroundColor Green }

Write-Host ""
$confirm = Read-Host "Continue with commit? (y/N)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "Cancelled by user" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Step 3: Committing changes..." -ForegroundColor Yellow
git commit -m "$Message"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to commit changes" -ForegroundColor Red
    exit 1
}

Write-Host "Step 4: Pushing to GitHub..." -ForegroundColor Yellow
git push origin test

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Debug Publish Complete ===" -ForegroundColor Green
Write-Host "✓ Changes committed to test branch" -ForegroundColor Green
Write-Host "✓ Pushed to GitHub" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Test your changes on the test branch" -ForegroundColor White
Write-Host "- When ready for production, run: .\git_publish_release.ps1" -ForegroundColor White
Write-Host ""
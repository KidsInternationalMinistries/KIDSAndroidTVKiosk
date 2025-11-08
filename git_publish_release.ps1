# ===================================================================
# KIDS Android TV Kiosk - Git Publish Release
# ===================================================================
# This script merges test branch to main and creates a production release
# Use this after testing on debug and ready for production deployment
# ===================================================================

param(
    [string]$Version = "",
    [string]$Message = ""
)

Write-Host "=== KIDS Android TV Kiosk - Git Publish Release ===" -ForegroundColor Green
Write-Host ""

# Check if we're on test branch
$currentBranch = git branch --show-current
if ($currentBranch -ne "test") {
    Write-Host "Error: You must be on the 'test' branch to publish release" -ForegroundColor Red
    Write-Host "Current branch: $currentBranch" -ForegroundColor Yellow
    Write-Host "Run: git checkout test" -ForegroundColor Yellow
    exit 1
}

# Check if test branch is clean and up to date
$status = git status --porcelain
if ($status) {
    Write-Host "Found uncommitted changes on test branch. Auto-committing..." -ForegroundColor Yellow
    
    # Clean up build files and add only source code (same as debug script)
    git clean -fd .gradle/ app/build/ build/ 2>$null
    git reset HEAD . 2>$null
    
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
    
    # Commit with automatic message
    $autoMessage = "Auto-commit before release - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    Write-Host "Committing changes with message: '$autoMessage'" -ForegroundColor Cyan
    git commit -m "$autoMessage"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to auto-commit changes" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "✓ Changes auto-committed" -ForegroundColor Green
}

# Get version number
if ([string]::IsNullOrEmpty($Version)) {
    Write-Host "Available tags:" -ForegroundColor Cyan
    git tag -l | Sort-Object | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
    Write-Host ""
    $Version = Read-Host "Enter version number (e.g., v1.0.0, v1.1.0)"
    if ([string]::IsNullOrEmpty($Version)) {
        Write-Host "Error: Version number is required" -ForegroundColor Red
        exit 1
    }
}

# Ensure version starts with 'v'
if (-not $Version.StartsWith("v")) {
    $Version = "v$Version"
}

# Check if tag already exists
$existingTag = git tag -l $Version
if ($existingTag) {
    Write-Host "Error: Tag '$Version' already exists" -ForegroundColor Red
    exit 1
}

# Get release message
if ([string]::IsNullOrEmpty($Message)) {
    $Message = Read-Host "Enter release message (or press Enter for default)"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = "Release $Version - $(Get-Date -Format 'yyyy-MM-dd')"
    }
}

Write-Host ""
Write-Host "Preparing release:" -ForegroundColor Cyan
Write-Host "  Version: $Version" -ForegroundColor White
Write-Host "  Message: $Message" -ForegroundColor White
Write-Host ""

$confirm = Read-Host "Continue with release? This will merge to main branch (y/N)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "Cancelled by user" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Step 1: Fetching latest changes..." -ForegroundColor Yellow
git fetch origin

Write-Host "Step 2: Switching to main branch..." -ForegroundColor Yellow
git checkout main

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to checkout main branch" -ForegroundColor Red
    exit 1
}

Write-Host "Step 3: Updating main branch..." -ForegroundColor Yellow
git pull origin main

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to pull main branch" -ForegroundColor Red
    exit 1
}

Write-Host "Step 4: Merging test branch into main..." -ForegroundColor Yellow
git merge test -m "Merge test branch for release $Version"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to merge test branch" -ForegroundColor Red
    Write-Host "You may need to resolve conflicts manually" -ForegroundColor Yellow
    exit 1
}

Write-Host "Step 5: Creating release tag..." -ForegroundColor Yellow
git tag -a $Version -m "$Message"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create tag" -ForegroundColor Red
    exit 1
}

Write-Host "Step 6: Pushing to GitHub..." -ForegroundColor Yellow
git push origin main
git push origin $Version

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red
    exit 1
}

Write-Host "Step 7: Switching back to test branch..." -ForegroundColor Yellow
git checkout test

Write-Host ""
Write-Host "=== Release Publish Complete ===" -ForegroundColor Green
Write-Host "✓ Test branch merged to main" -ForegroundColor Green
Write-Host "✓ Release tag '$Version' created" -ForegroundColor Green
Write-Host "✓ Pushed to GitHub" -ForegroundColor Green
Write-Host "✓ Back on test branch for continued development" -ForegroundColor Green
Write-Host ""
Write-Host "Release Information:" -ForegroundColor Cyan
Write-Host "  Version: $Version" -ForegroundColor White
Write-Host "  Branch: main" -ForegroundColor White
Write-Host "  GitHub: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Visit GitHub to see your release" -ForegroundColor White
Write-Host "- Download production APKs will be available from the main branch" -ForegroundColor White
Write-Host "- Continue development on test branch" -ForegroundColor White
Write-Host ""
# ===================================================================
# KIDS Android TV Kiosk - Git Publish Debug
# ===================================================================
# This script publishes your changes to the test branch for testing
# Use this after making changes and testing locally
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

# Get commit message
if ([string]::IsNullOrEmpty($Message)) {
    $Message = Read-Host "Enter commit message (or press Enter for default)"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = "Debug update - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }
}

Write-Host "Publishing to test branch with message: '$Message'" -ForegroundColor Cyan
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
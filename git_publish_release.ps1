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
    exit 1
}

# Auto-commit any uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Host "Auto-committing uncommitted changes..." -ForegroundColor Yellow
    
    # Clean up build files
    git clean -fd .gradle/ app/build/ build/ 2>$null
    git reset HEAD . 2>$null
    
    # Add source files
    git add .gitignore *.md *.json *.gradle *.properties *.bat *.ps1 gradlew* "app/src/" "gradle/"
    
    # Commit
    $autoMessage = "Auto-commit before release - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    git commit -m "$autoMessage"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to auto-commit changes" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Changes auto-committed" -ForegroundColor Green
}

# Get version number
if ([string]::IsNullOrEmpty($Version)) {
    $Version = Read-Host "Enter version number (e.g. v1.0.0)"
    if ([string]::IsNullOrEmpty($Version)) {
        Write-Host "Error: Version number is required" -ForegroundColor Red
        exit 1
    }
}

# Ensure version starts with 'v'
if (-not $Version.StartsWith("v")) {
    $Version = "v$Version"
}

# Get release message
if ([string]::IsNullOrEmpty($Message)) {
    $Message = Read-Host "Enter release message (or press Enter for default)"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = "Release $Version"
    }
}

Write-Host ""
Write-Host "Creating release:" -ForegroundColor Cyan
Write-Host "  Version: $Version" -ForegroundColor White
Write-Host "  Message: $Message" -ForegroundColor White
Write-Host ""

$confirm = Read-Host "Continue? (y/N)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "Cancelled" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Step 1: Fetching latest..." -ForegroundColor Yellow
git fetch origin

Write-Host "Step 2: Switching to main..." -ForegroundColor Yellow
git checkout main
if ($LASTEXITCODE -ne 0) { Write-Host "Error switching to main" -ForegroundColor Red; exit 1 }

Write-Host "Step 3: Updating main..." -ForegroundColor Yellow
git pull origin main
if ($LASTEXITCODE -ne 0) { Write-Host "Error updating main" -ForegroundColor Red; exit 1 }

Write-Host "Step 4: Merging test..." -ForegroundColor Yellow
git merge test -m "Merge test for release $Version"
if ($LASTEXITCODE -ne 0) { Write-Host "Error merging test" -ForegroundColor Red; exit 1 }

Write-Host "Step 5: Creating tag..." -ForegroundColor Yellow
git tag -a $Version -m "$Message"
if ($LASTEXITCODE -ne 0) { Write-Host "Error creating tag" -ForegroundColor Red; exit 1 }

Write-Host "Step 6: Pushing..." -ForegroundColor Yellow
git push origin main
git push origin $Version
if ($LASTEXITCODE -ne 0) { Write-Host "Error pushing" -ForegroundColor Red; exit 1 }

Write-Host "Step 7: Back to test..." -ForegroundColor Yellow
git checkout test

Write-Host ""
Write-Host "=== Release Complete ===" -ForegroundColor Green
Write-Host "Version: $Version" -ForegroundColor Green
Write-Host "GitHub: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor Cyan
Write-Host ""
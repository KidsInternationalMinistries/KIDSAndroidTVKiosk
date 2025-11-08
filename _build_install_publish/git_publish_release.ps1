# ===================================================================# ===================================================================

# KIDS Android TV Kiosk - Git Publish Release# KIDS Android TV Kiosk - Git Publish Release

# ===================================================================# ===================================================================

# This script builds and publishes a versioned release to GitHub# This script builds and publishes a versioned release to GitHub

# Creates a proper release with version tag and production APK# Creates a proper release with version tag and production APK

# ===================================================================# ===================================================================



param(param(

    [string]$Version = "",    [string]$Version = "",

    [string]$Message = ""    [string]$Message = ""

))



Write-Host "=== KIDS Android TV Kiosk - Git Publish Release ===" -ForegroundColor GreenWrite-Host "=== KIDS Android TV Kiosk - Git Publish Release ===" -ForegroundColor Green

Write-Host ""Write-Host ""



# Check if we're on test branch# Check if we're on test branch

$currentBranch = git branch --show-current$currentBranch = git branch --show-current

if ($currentBranch -ne "test") {if ($currentBranch -ne "test") {

    Write-Host "Error: You must be on the 'test' branch to publish release" -ForegroundColor Red    Write-Host "Error: You must be on the 'test' branch to publish release" -ForegroundColor Red

    Write-Host "Current branch: $currentBranch" -ForegroundColor Yellow    Write-Host "Current branch: $currentBranch" -ForegroundColor Yellow

    exit 1    exit 1

}}



# Check if GitHub CLI is available# Check if GitHub CLI is available

if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {

    Write-Host "Error: GitHub CLI (gh) is not installed or not in PATH" -ForegroundColor Red    Write-Host "Error: GitHub CLI (gh) is not installed or not in PATH" -ForegroundColor Red

    Write-Host "Please install GitHub CLI from: https://cli.github.com/" -ForegroundColor Yellow    Write-Host "Please install GitHub CLI from: https://cli.github.com/" -ForegroundColor Yellow

    Write-Host "Or use: winget install --id GitHub.cli" -ForegroundColor Yellow    Write-Host "Or use: winget install --id GitHub.cli" -ForegroundColor Yellow

    exit 1    exit 1

}}



# Check if logged into GitHub CLI# Check if logged into GitHub CLI

gh auth status 2>&1 | Out-Null$ghStatus = gh auth status 2>&1

if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Not logged into GitHub CLI" -ForegroundColor Red    Write-Host "Error: Not logged into GitHub CLI" -ForegroundColor Red

    Write-Host "Please run: gh auth login" -ForegroundColor Yellow    Write-Host "Please run: gh auth login" -ForegroundColor Yellow

    exit 1    exit 1

}}



# Get version from build.gradle if not provided# Get version from build.gradle if not provided

if ([string]::IsNullOrEmpty($Version)) {if ([string]::IsNullOrEmpty($Version)) {

    $buildGradle = Get-Content "app/build.gradle" -Raw    $buildGradle = Get-Content "app/build.gradle" -Raw

    if ($buildGradle -match 'versionName\s+"([^"]+)"') {    if ($buildGradle -match 'versionName\s+"([^"]+)"') {

        $Version = $matches[1]        $Version = $matches[1]

    } else {    } else {

        Write-Host "Error: Could not extract version from app/build.gradle" -ForegroundColor Red        Write-Host "Error: Could not extract version from app/build.gradle" -ForegroundColor Red

        exit 1        exit 1

    }    }

}}



# Ensure version starts with 'v'# Ensure version starts with 'v'

if (-not $Version.StartsWith("v")) {if (-not $Version.StartsWith("v")) {

    $Version = "v$Version"    $Version = "v$Version"

}}



Write-Host "Preparing release version: $Version" -ForegroundColor CyanWrite-Host "Preparing release version: $Version" -ForegroundColor Cyan



# Get commit message# Get commit message

if ([string]::IsNullOrEmpty($Message)) {if ([string]::IsNullOrEmpty($Message)) {

    $Message = Read-Host "Enter release notes (or press Enter for default)"    $Message = Read-Host "Enter release notes (or press Enter for default)"

    if ([string]::IsNullOrEmpty($Message)) {    if ([string]::IsNullOrEmpty($Message)) {

        $Message = "Release $Version - $(Get-Date -Format 'yyyy-MM-dd')"        $Message = "Release $Version - $(Get-Date -Format 'yyyy-MM-dd')"

    }    }

}}



Write-Host ""Write-Host ""

Write-Host "Step 1: Building release APK..." -ForegroundColor YellowWrite-Host "Step 1: Building release APK..." -ForegroundColor Yellow

./gradlew assembleRelease./gradlew assembleRelease



if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to build release APK" -ForegroundColor Red    Write-Host "Error: Failed to build release APK" -ForegroundColor Red

    exit 1    exit 1

}}



# Check if release APK exists# Check if release APK exists

$releaseApk = "app/build/outputs/apk/release/app-release.apk"$releaseApk = "app/build/outputs/apk/release/app-release.apk"

if (-not (Test-Path $releaseApk)) {if (-not (Test-Path $releaseApk)) {

    Write-Host "Error: Release APK not found at $releaseApk" -ForegroundColor Red    Write-Host "Error: Release APK not found at $releaseApk" -ForegroundColor Red

    exit 1    exit 1

}}



Write-Host "Step 2: Cleaning build files..." -ForegroundColor YellowWrite-Host "Step 2: Cleaning build files..." -ForegroundColor Yellow

# Clean build files that shouldn't be committed# Clean build files that shouldn't be committed

git checkout HEAD -- .gradle/ 2>$nullgit checkout HEAD -- .gradle/ 2>$null

git checkout HEAD -- app/build/ 2>$nullgit checkout HEAD -- app/build/ 2>$null

git checkout HEAD -- build/ 2>$nullgit checkout HEAD -- build/ 2>$null

git clean -fd .gradle/ app/build/ build/ 2>$nullgit clean -fd .gradle/ app/build/ build/ 2>$null

git reset HEAD . 2>$nullgit reset HEAD . 2>$null



Write-Host "Step 3: Adding source code changes..." -ForegroundColor YellowWrite-Host "Step 3: Adding source code changes..." -ForegroundColor Yellow

# Only add source files, not build artifacts# Only add source files, not build artifacts

git add app/src/git add app/src/

git add app/build.gradlegit add app/build.gradle

git add build.gradlegit add build.gradle

git add gradle.propertiesgit add gradle.properties

git add settings.gradlegit add settings.gradle

git add *.mdgit add *.md

git add _build_install_publish/git add _build_install_publish/



# Show what will be committed# Show what will be committed

Write-Host ""Write-Host ""

Write-Host "Files to be committed:" -ForegroundColor CyanWrite-Host "Files to be committed:" -ForegroundColor Cyan

git status --porcelaingit status --porcelain



$confirm = Read-Host "`nContinue with release $Version? (y/N)"$confirm = Read-Host "`nContinue with release $Version? (y/N)"

if ($confirm -ne "y" -and $confirm -ne "Y") {if ($confirm -ne "y" -and $confirm -ne "Y") {

    Write-Host "Cancelled by user" -ForegroundColor Yellow    Write-Host "Cancelled by user" -ForegroundColor Yellow

    exit 0    exit 0

}}



Write-Host ""Write-Host ""

Write-Host "Step 4: Committing changes..." -ForegroundColor YellowWrite-Host "Step 4: Committing changes..." -ForegroundColor Yellow

git commit -m "Release $Version"git commit -m "Release $Version"



if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "No changes to commit, proceeding with release..." -ForegroundColor Yellow    Write-Host "No changes to commit, proceeding with release..." -ForegroundColor Yellow

}}



Write-Host "Step 5: Merging to main branch..." -ForegroundColor YellowWrite-Host "Step 5: Merging to main branch..." -ForegroundColor Yellow

git checkout maingit checkout main

if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to checkout main branch" -ForegroundColor Red    Write-Host "Error: Failed to checkout main branch" -ForegroundColor Red

    exit 1    exit 1

}}



git merge test --no-ff -m "Merge test branch for release $Version"git merge test --no-ff -m "Merge test branch for release $Version"

if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to merge test branch" -ForegroundColor Red    Write-Host "Error: Failed to merge test branch" -ForegroundColor Red

    git checkout test    git checkout test

    exit 1    exit 1

}}



Write-Host "Step 6: Creating release tag..." -ForegroundColor YellowWrite-Host "Step 6: Creating release tag..." -ForegroundColor Yellow

git tag -a $Version -m "Release $Version"git tag -a $Version -m "Release $Version"

if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to create tag" -ForegroundColor Red    Write-Host "Error: Failed to create tag" -ForegroundColor Red

    git checkout test    git checkout test

    exit 1    exit 1

}}



Write-Host "Step 7: Pushing to GitHub..." -ForegroundColor YellowWrite-Host "Step 7: Pushing to GitHub..." -ForegroundColor Yellow

git push origin maingit push origin main

git push origin $Versiongit push origin $Version



if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red

    git checkout test    git checkout test

    exit 1    exit 1

}}



Write-Host "Step 8: Creating GitHub release..." -ForegroundColor YellowWrite-Host "Step 8: Creating GitHub release..." -ForegroundColor Yellow

gh release create $Version $releaseApk `gh release create $Version $releaseApk `

    --title "Release $Version" `    --title "Release $Version" `

    --notes "$Message" `    --notes "$Message" `

    --target main    --target main



if ($LASTEXITCODE -ne 0) {if ($LASTEXITCODE -ne 0) {

    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red

    git checkout test    git checkout test

    exit 1    exit 1

}}



Write-Host "Step 9: Returning to test branch..." -ForegroundColor YellowWrite-Host "Step 9: Returning to test branch..." -ForegroundColor Yellow

git checkout testgit checkout test



Write-Host ""Write-Host ""

Write-Host "=== Release Publish Complete ===" -ForegroundColor GreenWrite-Host "=== Release Publish Complete ===" -ForegroundColor Green

Write-Host "✓ Release APK built successfully" -ForegroundColor GreenWrite-Host "✓ Release APK built successfully" -ForegroundColor Green

Write-Host "✓ Changes merged to main branch" -ForegroundColor GreenWrite-Host "✓ Changes merged to main branch" -ForegroundColor Green

Write-Host "✓ Release tag $Version created" -ForegroundColor GreenWrite-Host "✓ Release tag $Version created" -ForegroundColor Green

Write-Host "✓ Pushed to GitHub" -ForegroundColor GreenWrite-Host "✓ Pushed to GitHub" -ForegroundColor Green

Write-Host "✓ GitHub release $Version created" -ForegroundColor GreenWrite-Host "✓ GitHub release $Version created" -ForegroundColor Green

Write-Host ""Write-Host ""

Write-Host "Release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor CyanWrite-Host "Release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor Cyan

Write-Host ""Write-Host ""

Write-Host "Next steps:" -ForegroundColor CyanWrite-Host "Next steps:" -ForegroundColor Cyan

Write-Host "- Verify the release works correctly" -ForegroundColor WhiteWrite-Host "- Verify the release works correctly" -ForegroundColor White

Write-Host "- Continue development on test branch" -ForegroundColor WhiteWrite-Host "- Continue development on test branch" -ForegroundColor White

Write-Host ""Write-Host ""
    
    # Add only source files - exclude build directories completely
    git add .gitignore *.md *.json *.gradle *.properties *.bat *.ps1 gradlew* "app/src/" "gradle/"
    
    # Only commit if there are actually changes to source files
    $statusAfterClean = git diff --cached --name-only
    if ($statusAfterClean) {
        $autoMessage = "Auto-commit before release - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
        git commit -m "$autoMessage"
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error: Failed to auto-commit changes" -ForegroundColor Red
            exit 1
        }
        
        Write-Host "Changes auto-committed" -ForegroundColor Green
    } else {
        Write-Host "No source changes to commit" -ForegroundColor Green
    }
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
# Stash any remaining uncommitted changes (build files etc)
git stash push -m "Temporary stash for release" 2>$null
git checkout main
if ($LASTEXITCODE -ne 0) { 
    Write-Host "Error switching to main" -ForegroundColor Red
    git stash pop 2>$null  # Restore stash if checkout failed
    exit 1 
}

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
# Restore any stashed build files
git stash pop 2>$null

Write-Host ""
Write-Host "=== Release Complete ===" -ForegroundColor Green
Write-Host "Version: $Version" -ForegroundColor Green
Write-Host "GitHub: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor Cyan
Write-Host ""
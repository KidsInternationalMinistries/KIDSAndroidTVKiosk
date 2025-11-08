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

# Extract version from build.gradle if not provided
if ([string]::IsNullOrWhiteSpace($Version)) {
    $buildGradle = Get-Content "app/build.gradle" -Raw
    if ($buildGradle -match 'versionName\s+"([^"]+)"') {
        $Version = "v" + $Matches[1]
    } else {
        Write-Host "Error: Could not extract version from app/build.gradle" -ForegroundColor Red
        exit 1
    }
}

# Use version as default message if no message provided
if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = "Release $Version"
}

Write-Host "Building and publishing release version: $Version" -ForegroundColor Cyan
Write-Host "Message: $Message" -ForegroundColor White
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

# Step 3: Build release APK
Write-Host "Step 3: Building release APK..." -ForegroundColor Yellow
.\gradlew assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build release APK" -ForegroundColor Red
    exit 1
}
Write-Host "Release APK built successfully" -ForegroundColor Green

# Step 4: Check if APK exists
$apkPath = "app/build/outputs/apk/release/app-release-unsigned.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "Error: Release APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

# Step 5: Create and push tag from current test branch
Write-Host "Step 5: Creating tag $Version from test branch..." -ForegroundColor Yellow
git tag -a $Version -m "$Message"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create tag" -ForegroundColor Red
    exit 1
}
Write-Host "Tag $Version created" -ForegroundColor Green

# Step 6: Push changes and tag to GitHub
Write-Host "Step 6: Pushing to GitHub..." -ForegroundColor Yellow
git push origin test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push test branch" -ForegroundColor Red
    exit 1
}

git push origin $Version
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push tag" -ForegroundColor Red
    exit 1
}

# Step 7: Create GitHub release with APK
Write-Host "Step 7: Creating GitHub release..." -ForegroundColor Yellow
gh release create $Version $apkPath --title "Release $Version" --notes "$Message"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Release Published Successfully ===" -ForegroundColor Green
Write-Host "Changes committed to test branch" -ForegroundColor Green
Write-Host "Release APK built" -ForegroundColor Green
Write-Host "Tag $Version created" -ForegroundColor Green
Write-Host "Pushed to GitHub" -ForegroundColor Green
Write-Host "GitHub release $Version created" -ForegroundColor Green
Write-Host ""
Write-Host "Release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Verify the release works correctly" -ForegroundColor White
Write-Host "- Update version in build.gradle for next release using: ._bump_version.ps1" -ForegroundColor White
Write-Host "- Continue development on test branch" -ForegroundColor White
Write-Host ""

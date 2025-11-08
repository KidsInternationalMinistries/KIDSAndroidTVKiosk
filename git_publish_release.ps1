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

# Step 1: Build release APK
Write-Host "Step 1: Building release APK..." -ForegroundColor Yellow
$buildResult = .\gradlew assembleRelease 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build release APK" -ForegroundColor Red
    Write-Host $buildResult
    exit 1
}
Write-Host "Release APK built successfully" -ForegroundColor Green

# Step 2: Commit any pending changes to test branch
Write-Host "Step 2: Committing changes to test branch..." -ForegroundColor Yellow
git add .
git commit -m $Message 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Changes committed to test branch" -ForegroundColor Green
} else {
    Write-Host "Note: No changes to commit" -ForegroundColor Yellow
}

# Step 3: Merge test into main
Write-Host "Step 3: Merging test branch into main..." -ForegroundColor Yellow
git checkout main
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to checkout main branch" -ForegroundColor Red
    exit 1
}

git merge test --no-ff -m "Merge test branch for release $Version"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to merge test into main" -ForegroundColor Red
    git checkout test
    exit 1
}
Write-Host "Test branch merged into main" -ForegroundColor Green

# Step 4: Create and push tag
Write-Host "Step 4: Creating tag $Version..." -ForegroundColor Yellow
git tag -a $Version -m $Message
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create tag" -ForegroundColor Red
    git checkout test
    exit 1
}
Write-Host "Tag $Version created" -ForegroundColor Green

# Step 5: Push main and tags
Write-Host "Step 5: Pushing to GitHub..." -ForegroundColor Yellow
git push origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push main branch" -ForegroundColor Red
    git checkout test
    exit 1
}

git push origin $Version
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push tag" -ForegroundColor Red
    git checkout test
    exit 1
}

# Step 6: Create GitHub release with APK
Write-Host "Step 6: Creating GitHub release..." -ForegroundColor Yellow
$apkPath = "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "Error: Release APK not found at $apkPath" -ForegroundColor Red
    git checkout test
    exit 1
}

gh release create $Version $apkPath --title "Release $Version" --notes $Message
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red
    git checkout test
    exit 1
}

# Step 7: Return to test branch
Write-Host "Step 7: Returning to test branch..." -ForegroundColor Yellow
git checkout test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: Failed to return to test branch" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Release Published Successfully ===" -ForegroundColor Green
Write-Host "Release APK built" -ForegroundColor Green
Write-Host "Code merged to main branch" -ForegroundColor Green
Write-Host "Tag $Version created" -ForegroundColor Green
Write-Host "Pushed to GitHub" -ForegroundColor Green
Write-Host "GitHub release $Version created" -ForegroundColor Green
Write-Host ""
Write-Host "Release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$Version" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "- Verify the release works correctly" -ForegroundColor White
Write-Host "- Update version in build.gradle for next release" -ForegroundColor White
Write-Host "- Continue development on test branch" -ForegroundColor White
Write-Host ""
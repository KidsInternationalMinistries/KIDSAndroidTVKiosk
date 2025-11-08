# ===================================================================
# KIDS Android TV Kiosk - Git Publish (Unified)
# ===================================================================
# This script builds and publishes either debug or release builds to GitHub
# Usage: 
#   ._git_publish.ps1 debug -Message "Debug test"
#   ._git_publish.ps1 release -Message "Release v1.3"
#   ._git_publish.ps1  (will prompt for build type)
# ===================================================================

param(
    [ValidateSet("debug", "release", "")]
    [string]$BuildType = "",
    [string]$Message = "",
    [string]$Version = ""
)

# Function to prompt for build type if not provided
function Get-BuildType {
    if ([string]::IsNullOrEmpty($BuildType)) {
        Write-Host "Select build type:" -ForegroundColor Cyan
        Write-Host "1. Debug (creates pre-release for testing)" -ForegroundColor White
        Write-Host "2. Release (creates versioned release)" -ForegroundColor White
        Write-Host ""
        
        do {
            $choice = Read-Host "Enter choice (1 or 2)"
            switch ($choice) {
                "1" { return "debug" }
                "2" { return "release" }
                default { Write-Host "Invalid choice. Please enter 1 or 2." -ForegroundColor Yellow }
            }
        } while ($true)
    }
    return $BuildType
}

# Get build type
$BuildType = Get-BuildType

Write-Host "=== KIDS Android TV Kiosk - Git Publish ($($BuildType.ToUpper())) ===" -ForegroundColor Green
Write-Host ""

# Check branch requirements based on build type
$currentBranch = git branch --show-current

if ($BuildType -eq "debug") {
    # Debug builds can be published from any branch
    Write-Host "Publishing debug from branch: $currentBranch" -ForegroundColor Cyan
} else {
    # Release builds should be from version branches
    if ($currentBranch -eq "main") {
        Write-Host "Error: Cannot publish release from 'main' branch" -ForegroundColor Red
        Write-Host "Please create a version branch first or switch to an existing version branch" -ForegroundColor Yellow
        Write-Host "Example: git checkout -b version/v1.4" -ForegroundColor Yellow
        exit 1
    }
    
    if ($currentBranch -eq "test") {
        Write-Host "Warning: Publishing release from 'test' branch" -ForegroundColor Yellow
        Write-Host "Consider creating a version branch: git checkout -b version/v1.4" -ForegroundColor Yellow
        $confirm = Read-Host "Continue anyway? (y/N)"
        if ($confirm -ne "y" -and $confirm -ne "Y") {
            Write-Host "Release cancelled" -ForegroundColor Yellow
            exit 0
        }
    }
    
    Write-Host "Publishing release from branch: $currentBranch" -ForegroundColor Cyan
}

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

# Configure build-specific variables
if ($BuildType -eq "debug") {
    $buildTask = "assembleDebug"
    $apkPath = "app/build/outputs/apk/debug/app-debug.apk"
    $releaseTag = "debug"
    $releaseTitle = "Debug Build"
    $releaseNotes = "Latest debug build for testing. This is automatically updated with each debug publish."
    $isPrerelease = $true
    $needsTag = $false
} else {
    # Release build
    $buildTask = "assembleRelease"
    $apkPath = "app/build/outputs/apk/release/app-release.apk"
    $isPrerelease = $false
    $needsTag = $true
    
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
    
    $releaseTag = $Version
    $releaseTitle = "Release $Version"
    $releaseNotes = if ([string]::IsNullOrWhiteSpace($Message)) { "Release $Version" } else { $Message }
}

# Get commit message
if ([string]::IsNullOrEmpty($Message)) {
    if ($BuildType -eq "debug") {
        $defaultMessage = "Debug update - $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    } else {
        $defaultMessage = "Release $Version"
    }
    
    $Message = Read-Host "Enter commit message (or press Enter for default: '$defaultMessage')"
    if ([string]::IsNullOrEmpty($Message)) {
        $Message = $defaultMessage
    }
}

Write-Host "Publishing $BuildType build with message: '$Message'" -ForegroundColor Cyan
if ($BuildType -eq "release") {
    Write-Host "Version: $Version" -ForegroundColor Cyan
}
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

# Step 3: Build APK
Write-Host "Step 3: Building $BuildType APK..." -ForegroundColor Yellow
& .\gradlew $buildTask
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build $BuildType APK" -ForegroundColor Red
    exit 1
}

# Check if APK exists
if (-not (Test-Path $apkPath)) {
    Write-Host "Error: $BuildType APK not found at $apkPath" -ForegroundColor Red
    exit 1
}
Write-Host "$BuildType APK built successfully" -ForegroundColor Green

# Step 4: Create tag for release builds
if ($needsTag) {
    Write-Host "Step 4: Creating tag $releaseTag..." -ForegroundColor Yellow
    git tag -a $releaseTag -m "$Message"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to create tag" -ForegroundColor Red
        exit 1
    }
    Write-Host "Tag $releaseTag created" -ForegroundColor Green
    $stepNum = 5
} else {
    $stepNum = 4
}

# Step: Push to GitHub
Write-Host "Step $stepNum`: Pushing to GitHub..." -ForegroundColor Yellow
git push origin $currentBranch
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push to GitHub" -ForegroundColor Red
    exit 1
}

# Push tag for release builds
if ($needsTag) {
    git push origin $releaseTag
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to push tag" -ForegroundColor Red
        exit 1
    }
}

$stepNum++

# Step: Create/update GitHub release
Write-Host "Step $stepNum`: Creating/updating GitHub release..." -ForegroundColor Yellow

if ($BuildType -eq "debug") {
    # Check if debug release already exists and delete it
    $existingRelease = & $ghCommand release view "debug" 2>$null
    if ($existingRelease) {
        Write-Host "Updating existing debug release..." -ForegroundColor Cyan
        & $ghCommand release delete "debug" --yes
    }
    Write-Host "Creating new debug pre-release..." -ForegroundColor Cyan
}

# Create GitHub release
$releaseArgs = @($releaseTag, $apkPath, "--title", $releaseTitle, "--notes", $releaseNotes)
if ($isPrerelease) {
    $releaseArgs += "--prerelease"
}
if ($BuildType -eq "debug") {
    $releaseArgs += "--target", $currentBranch
    # Debug builds: Only include APK, no source code (current behavior is correct)
} else {
    # Release builds: Should include source code for proper versioning
    # We can add this if needed, but let's first test current behavior
}

& $ghCommand release create @releaseArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to create GitHub release" -ForegroundColor Red
    exit 1
}

# Success summary
Write-Host ""
Write-Host "=== $($BuildType.ToUpper()) Publish Complete ===" -ForegroundColor Green
Write-Host "Changes committed to $currentBranch branch" -ForegroundColor Green
Write-Host "$BuildType APK built successfully" -ForegroundColor Green
Write-Host "Pushed to GitHub" -ForegroundColor Green
if ($needsTag) {
    Write-Host "Tag $releaseTag created" -ForegroundColor Green
}
Write-Host "$BuildType release created/updated" -ForegroundColor Green
Write-Host ""
Write-Host "Release available at: https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$releaseTag" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
if ($BuildType -eq "debug") {
    Write-Host "- Test the debug release on devices" -ForegroundColor White
    Write-Host "- When ready for production, run: .\_git_publish.ps1 release" -ForegroundColor White
} else {
    Write-Host "- Verify the release works correctly" -ForegroundColor White
    Write-Host "- Update version in build.gradle for next release using: ._bump_version.ps1" -ForegroundColor White
    Write-Host "- Continue development on current branch" -ForegroundColor White
}
Write-Host ""
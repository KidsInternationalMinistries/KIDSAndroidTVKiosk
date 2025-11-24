# Create Kiosk Bootstrap Release
# This creates a special "Kiosk Bootstrap" GitHub release for the updater app

Write-Host "Creating Kiosk Bootstrap release..." -ForegroundColor Green

# Check if bootstrap APK exists
$bootstrapApk = "kids-kiosk-bootstrap.apk"
if (-not (Test-Path $bootstrapApk)) {
    Write-Host "Bootstrap APK not found. Run _build_updater.ps1 first." -ForegroundColor Red
    exit 1
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
    Write-Host "GitHub CLI (gh) is not installed" -ForegroundColor Red
    Write-Host "Please install GitHub CLI:" -ForegroundColor Yellow
    Write-Host "  winget install GitHub.cli" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using GitHub CLI: $ghCommand" -ForegroundColor Green

# Check if logged into GitHub CLI
& $ghCommand auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Not logged into GitHub CLI" -ForegroundColor Red
    Write-Host "Please run: $ghCommand auth login" -ForegroundColor Yellow
    exit 1
}

# Create the release
$tagName = "kiosk-bootstrap"
$releaseTitle = "Kiosk Bootstrap"

try {
    # Delete existing bootstrap release if it exists
    Write-Host "Checking for existing bootstrap release..." -ForegroundColor Yellow
    & $ghCommand release delete $tagName --yes 2>$null
    
    # Delete the tag both locally and remotely
    git tag -d $tagName 2>$null
    git push origin --delete $tagName 2>$null
    
    # Create new tag
    Write-Host "Creating new bootstrap tag..." -ForegroundColor Yellow
    git tag $tagName
    git push origin $tagName
    
    # Create the release with simple notes
    Write-Host "Creating GitHub release: $releaseTitle" -ForegroundColor Green
    & $ghCommand release create $tagName $bootstrapApk --title "$releaseTitle" --notes "KIDS Kiosk Bootstrap app for installing and updating the main kiosk application. This small utility app downloads and installs the latest kiosk release." --latest=false
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Bootstrap release created successfully!" -ForegroundColor Green
        Write-Host "Release: $releaseTitle" -ForegroundColor Yellow
        Write-Host "Tag: $tagName" -ForegroundColor Yellow
        Write-Host "APK: $bootstrapApk" -ForegroundColor Yellow
        Write-Host "" -ForegroundColor Yellow
        Write-Host "Share this URL for easy bootstrap downloads:" -ForegroundColor Cyan
        Write-Host "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tag/$tagName" -ForegroundColor Cyan
    } else {
        Write-Host "Failed to create GitHub release" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Failed to create bootstrap release: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "Bootstrap release process completed!" -ForegroundColor Green
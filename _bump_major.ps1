# Bump Major Version
# This increments the major version number (e.g., 1.8 -> 1.9 or 1.9 -> 2.0)

param(
    [switch]$minor,  # Bump minor version (1.8 -> 1.9)
    [switch]$major   # Bump major version (1.8 -> 2.0)
)

if (-not $minor -and -not $major) {
    Write-Host "Usage: _bump_major.ps1 -minor OR _bump_major.ps1 -major" -ForegroundColor Red
    Write-Host "  -minor: Bump minor version (1.8 -> 1.9)" -ForegroundColor Yellow
    Write-Host "  -major: Bump major version (1.8 -> 2.0)" -ForegroundColor Yellow
    exit 1
}

Write-Host "Bumping version..." -ForegroundColor Green

# Read current version from build.gradle
$buildGradlePath = "app\build.gradle"
$buildGradleContent = Get-Content $buildGradlePath

# Extract current version name
$versionNameLine = $buildGradleContent | Where-Object { $_ -match '^\s*versionName\s+"([^"]+)"' }

if (-not $versionNameLine) {
    Write-Host "Could not find versionName in build.gradle" -ForegroundColor Red
    exit 1
}

$currentVersionName = ($versionNameLine -replace '^\s*versionName\s+"', '' -replace '".*$', '')

# Parse version number (expecting X.Y format)
if ($currentVersionName -match '^(\d+)\.(\d+)$') {
    $majorNum = [int]$matches[1]
    $minorNum = [int]$matches[2]
} else {
    Write-Host "Current version '$currentVersionName' is not in expected X.Y format" -ForegroundColor Red
    exit 1
}

# Calculate new version
if ($major) {
    $newMajor = $majorNum + 1
    $newMinor = 0
    $newVersionName = "$newMajor.$newMinor"
    $bumpType = "major"
} elseif ($minor) {
    $newMajor = $majorNum
    $newMinor = $minorNum + 1
    $newVersionName = "$newMajor.$newMinor"
    $bumpType = "minor"
}

Write-Host "Current version: $currentVersionName" -ForegroundColor Yellow
Write-Host "New version: $newVersionName" -ForegroundColor Yellow

# Confirm with user
$confirmation = Read-Host "Bump $bumpType version from $currentVersionName to $newVersionName? (y/N)"
if ($confirmation.ToLower() -ne 'y') {
    Write-Host "Version bump cancelled" -ForegroundColor Yellow
    exit 0
}

# Update version name in build.gradle
$updatedContent = $buildGradleContent -replace "versionName\s+`"$currentVersionName`"", "versionName `"$newVersionName`""
$updatedContent | Set-Content $buildGradlePath

Write-Host "Updated build.gradle with new version name" -ForegroundColor Green

# Commit changes
git add app\build.gradle
git commit -m "Bump $bumpType version to $newVersionName"
git push origin main

Write-Host "Version bumped to $newVersionName and pushed to git!" -ForegroundColor Green
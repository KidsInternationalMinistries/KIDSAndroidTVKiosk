# Promote Prerelease to Latest
# This promotes the most recent prerelease to be the "latest" release

Write-Host "Promoting prerelease to latest..." -ForegroundColor Green

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
    Write-Host "Or promote manually at: https://github.com/sbondCo/KIDSAndroidTVKiosk/releases" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using GitHub CLI: $ghCommand" -ForegroundColor Green

# Check if logged into GitHub CLI
$ghStatus = & $ghCommand auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Not logged into GitHub CLI" -ForegroundColor Red
    Write-Host "Please run: $ghCommand auth login" -ForegroundColor Yellow
    Write-Host "Or promote manually at: https://github.com/sbondCo/KIDSAndroidTVKiosk/releases" -ForegroundColor Yellow
    exit 1
}

# Get the prerelease (should always be tagged as "prerelease")
Write-Host "Fetching prerelease..." -ForegroundColor Yellow

try {
    $releases = & $ghCommand release list --exclude-drafts --json tagName,isPrerelease,name,createdAt | ConvertFrom-Json
    $prerelease = $releases | Where-Object { $_.tagName -eq "prerelease" -and $_.isPrerelease -eq $true } | Select-Object -First 1
    
    if (-not $prerelease) {
        Write-Host "No prerelease found with tag 'prerelease'" -ForegroundColor Red
        exit 1
    }
    
    $prereleaseTitle = $prerelease.name
    Write-Host "Found prerelease: $prereleaseTitle (tag: prerelease)" -ForegroundColor Yellow
    
    # Extract version from title and create clean production tag
    # Expected format from new script: "v1.8 (PreRelease)-build107"
    if ($prereleaseTitle -match '^v(\d+\.\d+)\s+\(PreRelease\)-build(\d+)$') {
        # Clean format: "v1.8 (PreRelease)-build107"
        $versionBase = $matches[1]
        $buildNumber = $matches[2]
        $newTagName = "v$versionBase-build$buildNumber"
        Write-Host "Promoting PreRelease version to stable: $newTagName" -ForegroundColor Yellow
    } elseif ($prereleaseTitle -match '^v(.+?)\s+\(PreRelease\)-Debug-build(\d+)\s+\(PreRelease\)$') {
        # Legacy debug format with -Debug in the middle
        $versionBase = $matches[1]
        $buildNumber = $matches[2]
        $newTagName = "v$versionBase-build$buildNumber"
        Write-Host "Promoting PreRelease debug version to stable: $newTagName" -ForegroundColor Yellow
    } elseif ($prereleaseTitle -match '^v(.+?)\s+\(PreRelease\)-build(\d+)\s+\(PreRelease\)$') {
        # Legacy format with double PreRelease
        $versionBase = $matches[1]
        $buildNumber = $matches[2]
        $newTagName = "v$versionBase-build$buildNumber"
        Write-Host "Promoting PreRelease version (legacy format) to stable: $newTagName" -ForegroundColor Yellow
    } elseif ($prereleaseTitle -match '^(v\d+\.\d+-build\d+)\s+\(PreRelease\)$') {
        # Simple format: "v1.8-build107 (PreRelease)"
        $newTagName = $matches[1]  # This will be like "v1.8-build107"
        Write-Host "Promoting PreRelease version (simple format) to stable: $newTagName" -ForegroundColor Yellow
    } else {
        Write-Host "Unexpected prerelease title format: $prereleaseTitle" -ForegroundColor Red
        Write-Host "Expected format: v1.8 (PreRelease)-build107" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Will promote to tag: $newTagName" -ForegroundColor Yellow
    
    # Download the release assets first
    Write-Host "Downloading release assets..." -ForegroundColor Yellow
    $tempDir = "temp_promotion"
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    & $ghCommand release download prerelease --dir $tempDir
    
    # Rename APK files to production format
    Write-Host "Renaming APK files to production format..." -ForegroundColor Yellow
    $apkFiles = Get-ChildItem -Path $tempDir -Filter "*.apk"
    foreach ($apkFile in $apkFiles) {
        # Create clean production filename: kidsandroidtvkiosk-v1.16-build125.apk
        $newApkName = "kidsandroidtvkiosk-$newTagName.apk"
        $newApkPath = Join-Path $tempDir $newApkName
        Move-Item $apkFile.FullName $newApkPath
        Write-Host "Renamed: $($apkFile.Name) -> $newApkName" -ForegroundColor Green
    }
    
    # Delete the old prerelease GitHub release (we'll recreate it as stable)
    Write-Host "Deleting old prerelease GitHub release..." -ForegroundColor Yellow
    & $ghCommand release delete prerelease --yes
    
    # Keep the prerelease git tag but move latest tag to this release
    Write-Host "Updating 'latest' tag to: $newTagName" -ForegroundColor Yellow
    git tag -d latest 2>$null
    git push origin --delete latest 2>$null
    git tag latest
    git push origin latest
    
    # Also create permanent version tag for this release
    Write-Host "Creating permanent version tag: $newTagName" -ForegroundColor Yellow
    git tag -d $newTagName 2>$null
    git push origin --delete $newTagName 2>$null
    git tag $newTagName
    git push origin $newTagName
    
    # Update build.gradle to remove "(PreRelease)" from versionName for future builds
    Write-Host "Updating build.gradle to remove (PreRelease) from versionName..." -ForegroundColor Yellow
    $buildGradlePath = "app\build.gradle"
    if (Test-Path $buildGradlePath) {
        $buildGradleContent = Get-Content $buildGradlePath
        $cleanVersionName = $newTagName -replace '^v(\d+\.\d+)-build\d+$', '$1'
        $updatedContent = $buildGradleContent -replace 'versionName\s+"[^"]*\s+\(PreRelease\)"', "versionName ""$cleanVersionName"""
        $updatedContent | Set-Content $buildGradlePath
        
        # Commit the versionName cleanup
        git add app\build.gradle
        git commit -m "Remove (PreRelease) from versionName after promoting $newTagName"
        git push origin main
        
        Write-Host "Updated build.gradle versionName to: $cleanVersionName" -ForegroundColor Green
    }
    
    # Create the new release as latest (not prerelease)
    Write-Host "Creating latest release with promoted content..." -ForegroundColor Green
    
    # Create the permanent version release
    Write-Host "Creating permanent version release: $newTagName" -ForegroundColor Green
    $assetFiles = Get-ChildItem -Path $tempDir -File | ForEach-Object { $_.FullName }
    & $ghCommand release create $newTagName $assetFiles --title "$newTagName" --notes "Release build"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Permanent release created: $newTagName" -ForegroundColor Green
        
        # Also create/update the latest release (pointing to same content)
        Write-Host "Updating latest release pointer..." -ForegroundColor Yellow
        & $ghCommand release delete latest --yes 2>$null
        & $ghCommand release create latest $assetFiles --title "$newTagName" --notes "Release build" --latest
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Successfully promoted prerelease to latest release!" -ForegroundColor Green
            Write-Host "Permanent tag: $newTagName" -ForegroundColor Yellow
            Write-Host "Latest tag: latest" -ForegroundColor Yellow
        } else {
            Write-Host "Warning: Failed to create latest release pointer" -ForegroundColor Yellow
        }
        
        # Clean up temp directory
        Remove-Item -Path $tempDir -Recurse -Force
        
        # Now create the next prerelease
        Write-Host "`nCreating next prerelease..." -ForegroundColor Cyan
        
        # Read current version from build.gradle
        $buildGradlePath = "app\build.gradle"
        $buildGradleContent = Get-Content $buildGradlePath
        
        # Extract current version code and version name
        $versionCodeLine = $buildGradleContent | Where-Object { $_ -match '^\s*versionCode\s+(\d+)' }
        $versionNameLine = $buildGradleContent | Where-Object { $_ -match '^\s*versionName\s+"([^"]+)"' }
        
        if ($versionCodeLine -match '^\s*versionCode\s+(\d+)') {
            $currentVersionCode = [int]$matches[1]
        } else {
            Write-Host "Could not parse versionCode from build.gradle" -ForegroundColor Red
            exit 1
        }
        
        if ($versionNameLine -match '^\s*versionName\s+"([^"]+)"') {
            $currentVersionName = $matches[1]
        } else {
            Write-Host "Could not parse versionName from build.gradle" -ForegroundColor Red
            exit 1
        }
        
        $nextVersionCode = $currentVersionCode + 1
        
        # Bump the minor version for the next prerelease
        if ($currentVersionName -match '^(\d+)\.(\d+)$') {
            $majorVersion = [int]$matches[1]
            $minorVersion = [int]$matches[2]
            $newMinorVersion = $minorVersion + 1
            $nextVersionName = "$majorVersion.$newMinorVersion"
            Write-Host "Bumping version from $currentVersionName to $nextVersionName" -ForegroundColor Cyan
        } else {
            Write-Host "Warning: Could not parse version format '$currentVersionName', using as-is" -ForegroundColor Yellow
            $nextVersionName = $currentVersionName
        }
        
        $prereleaseVersionName = "$nextVersionName (PreRelease)"
        
        Write-Host "Creating prerelease: versionCode $nextVersionCode, versionName $prereleaseVersionName" -ForegroundColor Yellow
        
        # Update version code and version name in build.gradle for next prerelease
        $updatedContent = $buildGradleContent -replace "(versionCode\s+)$currentVersionCode(\s*.*)", "`${1}$nextVersionCode`${2}"
        $updatedContent = $updatedContent -replace "(versionName\s+)""[^""]*""", "`${1}""$prereleaseVersionName"""
        $updatedContent | Set-Content $buildGradlePath
        
        # Build release APK for next prerelease
        Write-Host "Building next prerelease APK..." -ForegroundColor Green
        .\gradlew.bat clean assembleRelease
        
        if ($LASTEXITCODE -eq 0) {
            # Create git commit and tag for next prerelease
            $versionTag = "v$nextVersionName (PreRelease)-build$nextVersionCode"
            git add app\build.gradle
            git commit -m "Bump to $nextVersionCode for next prerelease $versionTag"
            
            # Create new prerelease tag
            git tag prerelease --force
            git push origin main
            git push origin prerelease --force
            
            # Create GitHub prerelease (use APK directly from build output)
            $sourceApk = "app\build\outputs\apk\release\app-release.apk"
            & $ghCommand release create prerelease $sourceApk --title "$versionTag" --notes "Prerelease build $nextVersionCode" --prerelease
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Next prerelease created successfully!" -ForegroundColor Green
                Write-Host "Version: $versionTag" -ForegroundColor Yellow
            } else {
                Write-Host "Failed to create next prerelease on GitHub" -ForegroundColor Red
            }
        } else {
            Write-Host "Failed to build next prerelease APK" -ForegroundColor Red
            # Revert build.gradle changes
            $buildGradleContent | Set-Content $buildGradlePath
        }
        
    } else {
        Write-Host "Failed to create new latest release" -ForegroundColor Red
        Write-Host "Assets are saved in: $tempDir" -ForegroundColor Yellow
        exit 1
    }
    
} catch {
    Write-Host "Failed to promote prerelease: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "You can manually promote at: https://github.com/sbondCo/KIDSAndroidTVKiosk/releases" -ForegroundColor Red
    exit 1
}
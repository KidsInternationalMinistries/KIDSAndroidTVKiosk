# Force Set Launcher - Debug Script for Xiaomi and other problematic devices
# This script uses ADB commands to force set the KIDS Kiosk app as default launcher

param(
    [switch]$Remove,
    [switch]$Status
)

# Colors for output
$Green = "Green"
$Red = "Red" 
$Yellow = "Yellow"
$Cyan = "Cyan"

function Write-Status {
    param($Message, $Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

function Check-AdbConnection {
    Write-Status "Checking ADB connection..." $Cyan
    $devices = & ".\platform-tools\adb.exe" devices 2>$null
    
    if ($LASTEXITCODE -ne 0) {
        Write-Status "ADB not found or not working" $Red
        Write-Status "Make sure platform-tools is in the project directory" $Yellow
        return $false
    }
    
    $connectedDevices = $devices | Select-String "device$"
    
    if (-not $connectedDevices) {
        Write-Status "No Android device connected" $Red
        Write-Status "Connect your Android TV and enable USB debugging" $Yellow
        return $false
    }
    
    Write-Status "Device connected successfully" $Green
    return $true
    if (-not $connectedDevices) {
        Write-Status "No devices connected" $Red
        Write-Status "Make sure your Android TV device is connected and USB debugging is enabled" $Yellow
        return $false
    }
    
    $deviceId = ($connectedDevices[0] -split "\s+")[0]
    Write-Status "Device connected: $deviceId" $Green
    return $true
}

function Get-LauncherStatus {
    Write-Status "`nChecking current launcher status..." $Cyan
    
    # Check HOME role assignment
    Write-Status "`nHOME Role Assignment:" $Yellow
    $roleOutput = & ".\platform-tools\adb.exe" shell dumpsys role | Select-String -Pattern "HOME" -Context 2
    $roleOutput | ForEach-Object { Write-Status $_ }
    
    # Check component states
    Write-Status "`nComponent States:" $Yellow
    $packageInfo = & ".\platform-tools\adb.exe" shell dumpsys package com.kidsim.tvkiosk | Select-String -Pattern "MainActivityHome|enabled|disabled" -Context 1
    $packageInfo | ForEach-Object { Write-Status $_ }
    
    # Check default launcher queries
    Write-Status "`nDefault Launcher Query:" $Yellow
    $launcherQuery = & ".\platform-tools\adb.exe" shell cmd package query-activities --brief -i android.intent.action.MAIN -c android.intent.category.HOME
    $launcherQuery | Where-Object { $_ -match "com.kidsim.tvkiosk|com.google.android.tvlauncher" } | ForEach-Object { Write-Status $_ }
}

function Enable-LauncherAlias {
    Write-Status "`nEnabling MainActivityHome alias..." $Cyan
    
    $result = & ".\platform-tools\adb.exe" shell pm enable com.kidsim.tvkiosk/.MainActivityHome 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Status "MainActivityHome alias enabled" $Green
        return $true
    } else {
        Write-Status "Failed to enable MainActivityHome alias" $Red
        Write-Status "Error: $result" $Red
        return $false
    }
}

function Set-HomeRole {
    Write-Status "`nSetting HOME role..." $Cyan
    
    # Remove current HOME role holders
    Write-Status "Removing existing HOME role assignments..." $Yellow
    & ".\platform-tools\adb.exe" shell cmd role remove-role-holder android.app.role.HOME com.google.android.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell cmd role remove-role-holder android.app.role.HOME com.xiaomi.mitv.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell cmd role remove-role-holder android.app.role.HOME com.xiaomi.mitv.home 2>$null
    
    # Add our app as HOME role holder
    $result = & ".\platform-tools\adb.exe" shell cmd role add-role-holder android.app.role.HOME com.kidsim.tvkiosk 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Status "HOME role assigned to com.kidsim.tvkiosk" $Green
        return $true
    } else {
        Write-Status "Failed to set HOME role" $Red
        Write-Status "Error: $result" $Red
        return $false
    }
}

function Clear-LauncherPreferences {
    Write-Status "`nClearing launcher preferences..." $Cyan
    
    # Clear package preferences
    & ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.google.android.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.xiaomi.mitv.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell pm clear-package-preferred-activities com.xiaomi.mitv.home 2>$null
    
    Write-Status "Launcher preferences cleared" $Green
}

function Set-PreferredActivity {
    Write-Status "`nSetting preferred activity..." $Cyan
    
    # Set our app as preferred for HOME intent
    $result = & ".\platform-tools\adb.exe" shell pm set-home-activity com.kidsim.tvkiosk/.MainActivityHome 2>&1
    
    if ($result -notmatch "Error|Exception") {
        Write-Status "Preferred activity set" $Green
        return $true
    } else {
        Write-Status "Could not set preferred activity (this is optional)" $Yellow
        return $false
    }
}

function Force-LauncherSelection {
    Write-Status "`nForcing launcher selection..." $Cyan
    
    # Kill current launcher processes
    & ".\platform-tools\adb.exe" shell am force-stop com.google.android.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell am force-stop com.xiaomi.mitv.tvlauncher 2>$null
    & ".\platform-tools\adb.exe" shell am force-stop com.xiaomi.mitv.home 2>$null
    
    # Launch HOME intent to trigger selection
    & ".\platform-tools\adb.exe" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>$null
    
    Write-Status "Forced launcher selection" $Green
}

function Remove-AsLauncher {
    Write-Status "`nRemoving app as launcher..." $Cyan
    
    # Disable our HOME alias
    & ".\platform-tools\adb.exe" shell pm disable com.kidsim.tvkiosk/.MainActivityHome 2>$null
    
    # Remove HOME role
    & ".\platform-tools\adb.exe" shell cmd role remove-role-holder android.app.role.HOME com.kidsim.tvkiosk 2>$null
    
    # Restore Google TV Launcher
    $result = & ".\platform-tools\adb.exe" shell cmd role add-role-holder android.app.role.HOME com.google.android.tvlauncher 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Status "Removed as launcher, restored Google TV Launcher" $Green
    } else {
        # Try Xiaomi launcher as fallback
        & ".\platform-tools\adb.exe" shell cmd role add-role-holder android.app.role.HOME com.xiaomi.mitv.tvlauncher 2>$null
        Write-Status "Removed as launcher, restored system launcher" $Green
    }
}

function Test-LauncherSetup {
    Write-Status "`nTesting launcher setup..." $Cyan
    
    # Test HOME intent
    $homeTest = & ".\platform-tools\adb.exe" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.kidsim.tvkiosk/.MainActivityHome 2>&1
    
    if ($homeTest -notmatch "Error|Exception|not found") {
        Write-Status "HOME intent test passed" $Green
        return $true
    } else {
        Write-Status "HOME intent test failed" $Red
        Write-Status "Error: $homeTest" $Red
        return $false
    }
}

# Main execution
Write-Status "KIDS TV Kiosk - Force Set Launcher Script" $Cyan
Write-Status "==========================================" $Cyan

if (-not (Check-AdbConnection)) {
    exit 1
}

if ($Status) {
    Get-LauncherStatus
    exit 0
}

if ($Remove) {
    Remove-AsLauncher
    Write-Status "`nLauncher removal complete!" $Green
    Write-Status "Reboot your device to ensure changes take effect." $Yellow
    exit 0
}

# Main launcher setup process
Write-Status "`nStarting launcher setup process..." $Cyan

$success = $true

# Step 1: Enable the launcher alias
if (-not (Enable-LauncherAlias)) {
    $success = $false
}

# Step 2: Clear existing launcher preferences
Clear-LauncherPreferences

# Step 3: Set HOME role
if (-not (Set-HomeRole)) {
    $success = $false
}

# Step 4: Set preferred activity (optional)
Set-PreferredActivity

# Step 5: Force launcher selection
Force-LauncherSelection

# Step 6: Test the setup
if (-not (Test-LauncherSetup)) {
    $success = $false
}

# Final status
Write-Status "`n" + "="*50 $Cyan
if ($success) {
    Write-Status "SUCCESS: Launcher setup completed!" $Green
    Write-Status "`nYour KIDS Kiosk app should now be the default launcher!" $Green
    Write-Status "Reboot your device to test AutoStart functionality." $Yellow
    
    # Show final status
    Write-Status "`nFinal Status Check:" $Cyan
    Get-LauncherStatus
    
} else {
    Write-Status "WARNING: Some steps failed, but the app may still work" $Yellow
    Write-Status "`nManual steps you can try:" $Cyan
    Write-Status "1. Go to Settings > Apps > Default Apps > Home App" $Yellow
    Write-Status "2. Select KIDS Kiosk Bootstrap" $Yellow
    Write-Status "3. Reboot the device" $Yellow
}

Write-Status "`nScript Options:" $Cyan
Write-Status ".\\_force_set_launcher.ps1           - Set as launcher" $Yellow
Write-Status ".\\_force_set_launcher.ps1 -Status   - Check status only" $Yellow  
Write-Status ".\\_force_set_launcher.ps1 -Remove   - Remove as launcher" $Yellow
@echo off
REM Debug Install Script for KIDS Android TV Kiosk
REM Builds the app and installs it on attached Android device

echo === KIDS Android TV Kiosk - Debug Install ===

REM Check if adb is available
if not exist "platform-tools\adb.exe" (
    echo Error: ADB not found at platform-tools\adb.exe
    echo Please ensure platform-tools is in the project directory
    pause
    exit /b 1
)

REM Check for connected devices
echo Checking for connected devices...
platform-tools\adb.exe devices | findstr "device$" >nul
if errorlevel 1 (
    echo Error: No Android devices connected
    echo Please connect an Android device with USB debugging enabled
    echo Available devices:
    platform-tools\adb.exe devices
    pause
    exit /b 1
)

echo Connected device found!

REM Build the app
echo Building the app...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo Error: Build failed
    pause
    exit /b 1
)

echo Build successful!

REM Copy APK to root directory
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo Error: APK not found
    pause
    exit /b 1
)

copy "app\build\outputs\apk\debug\app-debug.apk" "app-debug-install.apk" >nul
echo APK copied to app-debug-install.apk

REM Install on device
echo Installing on device...
platform-tools\adb.exe install -r "app-debug-install.apk"
if errorlevel 1 (
    echo Error: Installation failed
    pause
    exit /b 1
)

echo Installation successful!

REM Clear logcat for fresh logs
echo Clearing logcat...
platform-tools\adb.exe logcat -c

REM Start the app
echo Starting the main activity...
platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.MainActivity

echo.
echo === Debug Install Complete ===
echo App installed and started successfully!
echo.
echo Useful commands:
echo   View logs: platform-tools\adb.exe logcat ^| findstr "tvkiosk"
echo   Start main app: platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.MainActivity
echo   Start update app: platform-tools\adb.exe shell am start -n com.kidsim.tvkiosk/.UpdateActivity
echo   Uninstall: platform-tools\adb.exe uninstall com.kidsim.tvkiosk
echo.
pause
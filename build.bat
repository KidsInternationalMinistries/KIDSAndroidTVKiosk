@echo off
echo Building Android TV Kiosk APK...
echo.

REM Check if Java is installed
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install Java JDK 8 or higher.
    pause
    exit /b 1
)

REM Check if Android SDK is configured
if "%ANDROID_HOME%"=="" (
    echo WARNING: ANDROID_HOME is not set.
    echo You may need to set ANDROID_HOME environment variable to your Android SDK path.
    echo Example: C:\Users\YourName\AppData\Local\Android\Sdk
    echo.
)

echo Building debug APK...
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    echo Make sure you have Android SDK installed and configured.
    pause
    exit /b 1
)

echo.
echo SUCCESS: APK built successfully!
echo.
echo You can find the APK at:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on Android TV:
echo 1. Enable Developer Options and USB Debugging on your Android TV
echo 2. Connect via ADB or use a file manager app
echo 3. Install the APK file
echo.
echo To install via ADB:
echo adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause
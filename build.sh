#!/bin/bash

echo "Building Android TV Kiosk APK..."
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH."
    echo "Please install Java JDK 8 or higher."
    exit 1
fi

# Check if Android SDK is configured
if [ -z "$ANDROID_HOME" ]; then
    echo "WARNING: ANDROID_HOME is not set."
    echo "You may need to set ANDROID_HOME environment variable to your Android SDK path."
    echo "Example: /Users/YourName/Library/Android/sdk"
    echo
fi

echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo
    echo "ERROR: Build failed!"
    echo "Make sure you have Android SDK installed and configured."
    exit 1
fi

echo
echo "SUCCESS: APK built successfully!"
echo
echo "You can find the APK at:"
echo "app/build/outputs/apk/debug/app-debug.apk"
echo
echo "To install on Android TV:"
echo "1. Enable Developer Options and USB Debugging on your Android TV"
echo "2. Connect via ADB or use a file manager app"
echo "3. Install the APK file"
echo
echo "To install via ADB:"
echo "adb install app/build/outputs/apk/debug/app-debug.apk"
echo
# Android TV Kiosk App

This is a kiosk application for Android TV that displays a web page and automatically refreshes it every hour.

## Features
- Displays https://sponsor.kidsim.org in fullscreen kiosk mode
- Auto-refreshes the page every hour
- Optimized for Android TV version 11 (API level 30)
- Fullscreen immersive mode with no system UI
- Prevents navigation away from the target page

## Building the APK

### Prerequisites
1. Java JDK 8 or higher
2. Android SDK (optional but recommended)

### Windows
```batch
build.bat
```

### Linux/macOS
```bash
chmod +x build.sh
./build.sh
```

### Manual build
```batch
gradlew.bat assembleDebug
```

## Installation

The APK will be generated at: `app\build\outputs\apk\debug\app-debug.apk`

### Install via ADB
1. Enable Developer Options on your Android TV
2. Enable USB Debugging
3. Connect your Android TV to your computer
4. Run: `adb install app\build\outputs\apk\debug\app-debug.apk`

### Install via File Manager
1. Copy the APK to a USB drive
2. Use a file manager app on your Android TV to install the APK
3. Enable "Install from Unknown Sources" if prompted

## Configuration

To change the target URL or refresh interval, edit `MainActivity.java`:

```java
// Change the URL
private static final String TARGET_URL = "https://your-website.com";

// Change refresh interval (in milliseconds)
private static final long REFRESH_INTERVAL = 3600000; // 1 hour
```

## App Behavior

- The app launches in fullscreen kiosk mode
- System UI (navigation bar, status bar) is hidden
- Back button is disabled to prevent exiting
- Screen stays on while the app is running
- Page refreshes automatically every hour
- Internet permission is required for web content

## Troubleshooting

### Build Issues
- Ensure Java is installed and in your PATH
- Set ANDROID_HOME environment variable if using Android SDK
- Run `gradlew clean` before building if you encounter issues

### Installation Issues
- Enable "Unknown Sources" in Android TV settings
- Check that USB Debugging is enabled
- Verify ADB connection with `adb devices`

### Runtime Issues
- Ensure Android TV has internet connectivity
- Check that the target URL is accessible
- Verify Android TV is running Android 11 or higher
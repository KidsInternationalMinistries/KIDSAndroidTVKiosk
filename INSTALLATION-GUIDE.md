# Android TV Kiosk - Installation Instructions

## Quick Installation for Android TV

### Method 1: Google Drive Download (Easiest)

1. **Upload APK to Google Drive:**
   - Upload `app-test.apk` to your Google Drive
   - Right-click → Get shareable link
   - Set permissions to "Anyone with the link can view"
   - Copy the download link

2. **Install on Android TV:**
   - Install "Downloader" app from Google Play Store
   - Open Downloader app
   - Enter your Google Drive direct download link
   - Install the APK

### Method 2: USB Drive Installation

1. **Copy to USB:**
   - Copy `app-test.apk` to USB drive
   - Insert USB into Android TV

2. **Install via File Manager:**
   - Open file manager on Android TV
   - Navigate to USB drive
   - Select `app-test.apk` and install

### Method 3: ADB Installation (if USB debugging enabled)

```bash
adb connect YOUR_TV_IP_ADDRESS
adb install app-test.apk
```

## Test Configuration

Your device with ID "test" will show:

1. **sponsor.kidsim.org** (30 seconds)
2. **Google Spreadsheet 1** (30 seconds)  
3. **Google Spreadsheet 2** (30 seconds)

- **Refresh**: Every 5 minutes
- **Update Button**: Red button in top-right corner
- **Cache**: Cleared on each refresh for fresh content

## Troubleshooting

- **Device ID**: Check logs to find actual device ID if "test" doesn't work
- **Unknown Sources**: Enable installation from unknown sources in Android TV settings
- **Network**: Ensure Android TV has internet connection for configuration loading

## URLs for Testing

- **Test Config**: https://raw.githubusercontent.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/test/config.json
- **Production Config**: https://raw.githubusercontent.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/main/config.json
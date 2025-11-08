# KIDS Android TV Kiosk - Manual Installation Guide

## Simplified Installation Process

The app has been simplified to have two separate versions that can coexist:

### APK Files
- **KIDSTVKiosk-Release.apk** - Production version (blue theme)
- **KIDSTVKiosk-Debug.apk** - Debug version (red theme)

### Manual Installation via ADB

1. **Connect to Android TV device**:
   ```
   adb connect [IP_ADDRESS]:5555
   ```

2. **Install Release Version**:
   ```
   adb install -r KIDSTVKiosk-Release.apk
   ```

3. **Install Debug Version**:
   ```
   adb install -r KIDSTVKiosk-Debug.apk
   ```

### App Behavior

- **Release App**: 
  - Package: `com.kidsim.tvkiosk`
  - Updates automatically to latest GitHub releases
  - Blue theme
  - Button: "Download Release Update"

- **Debug App**: 
  - Package: `com.kidsim.tvkiosk.debug`
  - Updates automatically to latest debug GitHub releases  
  - Red theme
  - Button: "Download Debug Update"

### No More Complexity

- ✅ No dropdown menus for build type selection
- ✅ Each app knows what it is automatically
- ✅ Simple manual installation process
- ✅ Both apps can be installed simultaneously
- ✅ Clean, straightforward user interface

### Update Process

1. Each app automatically detects its type (debug vs release)
2. Debug app only looks for debug releases
3. Release app only looks for production releases
4. No user selection needed - completely automatic

This eliminates all the complexity while maintaining full functionality!
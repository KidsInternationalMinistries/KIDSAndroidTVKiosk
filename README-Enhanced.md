# Android TV Kiosk App - Enhanced Version

An enterprise-grade Android TV kiosk application with GitHub-based configuration management, multi-page rotation, auto-start functionality, and comprehensive error handling.

## Features

### ✅ Core Functionality
- **WebView-based Display**: Full-screen web page rendering with JavaScript support
- **Auto-Start**: Automatically launches on device boot and after app updates
- **Kiosk Mode**: Immersive fullscreen experience with hidden system UI
- **Keep Screen On**: Prevents screen timeout during operation

### ✅ Enhanced Features
- **GitHub Configuration**: Load device-specific settings from GitHub repository
- **Multi-Page Rotation**: Cycle through multiple web pages with configurable timing
- **Dynamic Orientation**: Support for both landscape and portrait modes
- **Error Handling**: User-friendly error screens with automatic retry functionality
- **Memory Management**: Watchdog service to prevent memory leaks and app hangs
- **Device-Specific Config**: Different settings per device using device ID

## Configuration System

### GitHub Integration
The app loads configuration from a GitHub repository JSON file. Update the URL in `ConfigurationManager.java`:

```java
private static final String DEFAULT_CONFIG_URL = 
    "https://raw.githubusercontent.com/your-org/kiosk-config/main/config.json";
```

### Configuration Format
See `config-sample.json` for a complete example. Key structure:

```json
{
  "configVersion": "2.0",
  "devices": [
    {
      "deviceId": "tv001",
      "deviceName": "Lobby TV", 
      "orientation": "landscape",
      "refreshIntervalMinutes": 60,
      "autoStart": true,
      "pages": [
        {
          "url": "https://sponsor.kidsim.org",
          "displayTimeSeconds": 600,
          "title": "Sponsor Page",
          "enableJavaScript": true,
          "clearCache": false
        }
      ]
    }
  ]
}
```

### Device Identification
Devices are identified using Android ID. You can find your device ID in the app logs:
```
ConfigurationManager: Device ID: abc123def456
```

## Build and Deployment

### Prerequisites
- Android SDK API Level 30+
- Java JDK 17
- Gradle 7.0+

### Building the APK
Use the provided PowerShell script:
```powershell
.\build.ps1
```

Or build manually:
```bash
./gradlew assembleDebug
```

### Installation
1. Enable "Unknown Sources" in Android TV settings
2. Install via ADB:
   ```bash
   adb install app-debug.apk
   ```
3. Or copy APK to USB drive and install via file manager

## Configuration Management

### Setting Up GitHub Repository
1. Create a public GitHub repository for configuration
2. Add a `config.json` file with your device configurations
3. Update the URL in `ConfigurationManager.java`
4. Build and deploy the app

### Configuration Updates
- App checks for configuration updates every hour
- Changes to GitHub config are automatically applied
- No need to rebuild/redeploy the app for configuration changes

### Per-Device Configuration
- Each device can have unique settings
- Different page rotation schedules
- Different orientations (lobby vs reception)
- Different refresh intervals

## Auto-Start Configuration

The app automatically starts in these scenarios:
- **Device Boot**: Launches when Android TV boots up
- **App Update**: Restarts after app installation/update
- **Manual Launch**: Available in Android TV launcher

### Disabling Auto-Start
To disable auto-start for testing:
1. Update configuration with `"autoStart": false`
2. Or temporarily disable the BootReceiver in AndroidManifest.xml

## Error Handling

### Network Errors
- Displays user-friendly error message
- Shows retry button for manual retry
- Automatic retry every 5 minutes
- Logs detailed error information

### Configuration Errors
- Falls back to default configuration
- Shows toast notifications for config update failures
- Continues operation with last known good config

### Memory Management
- WatchdogService monitors app health every 5 minutes
- Automatic garbage collection when memory usage is high
- App restart if memory issues persist
- WebView cleanup on activity destroy

## Monitoring and Debugging

### Log Tags
- `MainActivity`: Main app lifecycle and page loading
- `ConfigurationManager`: Configuration loading and GitHub updates
- `WatchdogService`: Memory monitoring and health checks
- `BootReceiver`: Auto-start functionality

### Key Log Messages
```
MainActivity: Configuration updated from GitHub
ConfigurationManager: Device ID: abc123def456
WatchdogService: Memory: Used=45MB, Available=105MB, Max=150MB
BootReceiver: Boot completed, starting MainActivity
```

### Remote Debugging
Enable ADB over network for remote monitoring:
```bash
adb connect [TV_IP_ADDRESS]:5555
adb logcat | grep -E "(MainActivity|ConfigurationManager|WatchdogService)"
```

## Troubleshooting

### App Not Auto-Starting
1. Check if BOOT_COMPLETED permission is granted
2. Verify BootReceiver is enabled in AndroidManifest
3. Check device logs for boot receiver messages

### Configuration Not Loading
1. Verify GitHub URL is accessible from device
2. Check JSON syntax in configuration file
3. Ensure device has internet connectivity
4. Review ConfigurationManager logs

### Memory Issues
1. Monitor WatchdogService logs for memory warnings
2. Reduce number of pages if memory usage is high
3. Enable cache clearing for problematic pages
4. Check for WebView memory leaks

### Page Loading Issues
1. Test URLs manually in browser
2. Check JavaScript console for errors
3. Verify CORS settings on web server
4. Enable clearCache for problematic pages

## Advanced Configuration

### Custom Display Times
```json
"pages": [
  {
    "url": "https://urgent-announcements.com",
    "displayTimeSeconds": 60,
    "clearCache": true
  },
  {
    "url": "https://regular-content.com", 
    "displayTimeSeconds": 1800,
    "clearCache": false
  }
]
```

### Orientation Switching
```json
{
  "deviceId": "portrait-tv",
  "orientation": "portrait",
  "pages": [...]
}
```

### Cache Management
- Set `clearCache: true` for dynamic content
- Set `clearCache: false` for static content
- Reduces bandwidth usage and improves performance

## Security Considerations

- Configuration repository should be public or use personal access tokens
- Consider HTTPS for all page URLs
- WebView security settings are configured for kiosk use
- Auto-start permissions require user consent

## Support and Maintenance

### Regular Maintenance
- Monitor device memory usage via logs
- Update configuration as needed via GitHub
- Test page loading periodically
- Review error logs for issues

### Version Updates
- Update `configVersion` when making breaking changes
- Test configuration changes before deployment
- Use device-specific configs for gradual rollouts

---

**Version**: 2.0  
**Last Updated**: December 2024  
**Compatibility**: Android TV API Level 30+
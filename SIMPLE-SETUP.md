# Test/Production Configuration System - Quick Start

## 🎯 Simple Concept

Instead of version numbers, your kiosk app now uses a **device ID flag** system:

- **Test Devices**: Automatically load configuration from the `test` branch
- **Production Devices**: Automatically load configuration from the `main` branch  
- **One APK**: Same APK works for both test and production devices

## 🔧 How to Set It Up

### 1. Add Your Test Device IDs

Edit `ConfigurationManager.java` and add your test device IDs:

```java
String[] testDeviceIds = {
    "abc123def456",     // Your development device  
    "test_device_01",   // Test TV #1
    "test_device_02"    // Test TV #2
};
```

**To find your device ID**: Install the app and check the logs:
```
ConfigurationManager: Device abc123def456 using PRODUCTION configuration
```

### 2. Create GitHub Repository

Create a repository with two branches:

```
your-kiosk-config/
├── main branch → config.json (production)
└── test branch → config.json (testing)
```

### 3. Update URLs in App

Update the URLs in `ConfigurationManager.java`:
```java
private static final String PRODUCTION_CONFIG_URL = 
    "https://raw.githubusercontent.com/YOUR-ORG/kiosk-config/main/config.json";
private static final String TEST_CONFIG_URL = 
    "https://raw.githubusercontent.com/YOUR-ORG/kiosk-config/test/config.json";
```

## 🚀 Daily Workflow

### Testing New Configuration

1. **Edit test branch** on GitHub:
   ```bash
   git checkout test
   # Edit config.json with your test settings
   git commit -m "Test: New page rotation"
   git push
   ```

2. **Test devices automatically update** within 1 hour (or restart app for immediate)

3. **Verify it works** on test devices

### Promoting to Production

When test is working well:

```bash
git checkout main  
git merge test     # Copy test changes to production
git push
```

**All production devices automatically update** within 1 hour!

## 📊 Configuration Examples

### Test Configuration (Faster, Cache Clearing)
```json
{
  "environment": "test",
  "deviceName": "Test TV",
  "clearCache": true,
  "refreshIntervalMinutes": 5,
  "pages": [
    {
      "url": "https://sponsor.kidsim.org",
      "displayTimeSeconds": 60,
      "title": "Sponsor Page"
    },
    {
      "url": "https://test-page.example.com", 
      "displayTimeSeconds": 30,
      "title": "Test Page"
    }
  ]
}
```

### Production Configuration (Stable, Cache Optimized)
```json
{
  "environment": "production",
  "deviceName": "Production TV", 
  "clearCache": false,
  "refreshIntervalMinutes": 60,
  "pages": [
    {
      "url": "https://sponsor.kidsim.org",
      "displayTimeSeconds": 1800,
      "title": "Sponsor Page"
    }
  ]
}
```

## 🔍 Key Differences

| Aspect | Test Configuration | Production Configuration |
|--------|-------------------|-------------------------|
| **Page Timing** | 30-120 seconds | 15-30 minutes |
| **Refresh Rate** | 2-5 minutes | 30-60 minutes |
| **Cache Clearing** | Device-level enabled | Device-level disabled |
| **Test Pages** | Include test URLs | Only production URLs |
| **Update Frequency** | Every 5 minutes | Every hour |

## ✅ Benefits

1. **No Rebuilding**: Change configurations without rebuilding the APK
2. **Safe Testing**: Test devices isolated from production
3. **Instant Updates**: Push to GitHub, devices update automatically  
4. **Easy Rollback**: Git revert if something goes wrong
5. **One APK**: Same APK file works everywhere
6. **Device-Specific**: Each device can have different settings

## 🎯 What You Need to Do

1. **Find your device IDs** (install app, check logs)
2. **Add test device IDs** to `ConfigurationManager.java` 
3. **Create GitHub repo** with main/test branches
4. **Update GitHub URLs** in `ConfigurationManager.java`
5. **Build and deploy** the APK once
6. **Upload configurations** to GitHub branches

After that, you only need to edit GitHub files - no more app building for configuration changes!

---

**Result**: Professional configuration management with git-based workflow and automatic test/production deployment! 🎉
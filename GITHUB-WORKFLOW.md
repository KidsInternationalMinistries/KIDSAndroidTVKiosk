# GitHub Test/Production Configuration Workflow

## Overview

The enhanced kiosk app now supports automatic test/production configuration switching based on device ID flags. This allows you to:
- Test new configurations on specific devices before rolling out to production
- Keep production systems stable while experimenting with test configurations
- Easily promote test configurations to production through Git branches

## How It Works

### Device Configuration Logic

1. **Test Devices**: Devices with IDs listed in the `testDeviceIds` array automatically load from the `test` branch
2. **Production Devices**: All other devices load from the `main` branch (production)

### Configuration URLs

- **Production**: `https://raw.githubusercontent.com/your-org/kiosk-config/main/config.json`
- **Test**: `https://raw.githubusercontent.com/your-org/kiosk-config/test/config.json`

## GitHub Repository Setup

### Step 1: Create Repository Structure

```
your-github-repo/
├── main branch (production)
│   └── config.json
└── test branch (testing)
    └── config.json
```

### Step 2: Initial Setup Commands

```bash
# Create the repository
git init
git add config-production.json
git commit -m "Initial production configuration"
git branch -M main
git remote add origin https://github.com/your-org/kiosk-config.git
git push -u origin main

# Create test branch
git checkout -b test
git rm config-production.json
git add config-test.json
git mv config-test.json config.json
git commit -m "Initial test configuration"
git push -u origin test

# Go back to main and set up production config
git checkout main
git mv config-production.json config.json
git commit -m "Set up production config.json"
git push origin main
```

## Configuration Process

### Adding Test Devices

1. **Find Device ID**: Deploy the app and check logs for device ID:
   ```
   ConfigurationManager: Device abc123def456 using PRODUCTION configuration
   ```

2. **Add to Test List**: Update `ConfigurationManager.java`:
   ```java
   String[] testDeviceIds = {
       "abc123def456",     // Your development device
       "test_device_01",   // Test TV #1
       "test_device_02"    // Test TV #2
   };
   ```

3. **Rebuild and Deploy**: Only need to rebuild when adding/removing test devices

## Development Workflow

### Testing New Configurations

1. **Modify Test Configuration**:
   ```bash
   git checkout test
   # Edit config.json with your test settings
   git add config.json
   git commit -m "Test: Updated page rotation timing"
   git push origin test
   ```

2. **Test Devices Auto-Update**: Test devices will automatically pull new config within 1 hour (or restart app for immediate update)

3. **Verify on Test Devices**: Check logs to confirm test config loaded:
   ```
   ConfigurationManager: Device abc123def456 flagged for TEST configuration
   ConfigurationManager: Using TEST configuration for device: abc123def456
   ```

### Promoting to Production

When test configuration is working properly:

1. **Merge Test to Production**:
   ```bash
   git checkout main
   git merge test
   git push origin main
   ```

   OR copy changes manually:
   ```bash
   git checkout main
   # Copy the working parts from test config to main config
   git add config.json
   git commit -m "Production: Updated page rotation timing (tested)"
   git push origin main
   ```

2. **Production Devices Auto-Update**: Production devices automatically get the new config within 1 hour

## Example Workflow: Adding New Page

### Step 1: Test the Change
```bash
git checkout test
# Edit config.json with your test settings
{
  "deviceName": "Test TV",
  "clearCache": true,
  "refreshIntervalMinutes": 5,
  "pages": [
    {"url": "https://sponsor.kidsim.org", "displayTimeSeconds": 60},
    {"url": "https://new-content.example.com", "displayTimeSeconds": 30}
  ]
}
git commit -m "Test: Added new content page"
git push origin test
```

### Step 2: Verify on Test Devices
- Watch logs: `adb logcat | grep ConfigurationManager`
- Confirm new page loads correctly
- Check timing and transitions

### Step 3: Promote to Production
```bash
git checkout main
# Update config.json with production timing
{
  "deviceName": "Production TV",
  "clearCache": false,
  "refreshIntervalMinutes": 60,
  "pages": [
    {"url": "https://sponsor.kidsim.org", "displayTimeSeconds": 1800},
    {"url": "https://new-content.example.com", "displayTimeSeconds": 600}
  ]
}
git commit -m "Production: Added new content page (tested)"
git push origin main
```

## Configuration Differences: Test vs Production

### Test Configuration Features
- **Faster Rotation**: Short display times (60-180 seconds) for quick testing
- **Frequent Updates**: 2-5 minute refresh intervals
- **Cache Clearing**: `clearCache: true` at device level for fresh content
- **Test Pages**: Include test URLs for validation
- **Debug Timing**: Shorter intervals for faster iteration

### Production Configuration Features
- **Stable Timing**: Longer display times (15-30 minutes)
- **Standard Updates**: 30-60 minute refresh intervals  
- **Cache Optimization**: `clearCache: false` at device level for better performance
- **Production URLs**: Only live, stable content
- **Optimized Performance**: Settings tuned for reliability

## Monitoring and Troubleshooting

### Log Messages to Watch

```bash
# Device classification
ConfigurationManager: Device abc123def456 flagged for TEST configuration
ConfigurationManager: Device xyz789uvw012 using PRODUCTION configuration

# Configuration loading
ConfigurationManager: Using TEST configuration for device: abc123def456
ConfigurationManager: Configuration updated successfully from https://raw.githubusercontent.com/.../test/config.json

# Errors
ConfigurationManager: Failed to update configuration from https://raw.githubusercontent.com/.../test/config.json
```

### Common Issues

1. **Test Device Not Using Test Config**:
   - Check device ID in logs matches `testDeviceIds` array
   - Rebuild app after adding new test device IDs

2. **Configuration Not Updating**:
   - Check GitHub raw URL is accessible
   - Verify JSON syntax in config files
   - Check network connectivity on device

3. **Wrong Configuration Loading**:
   - Confirm device ID classification in logs
   - Verify branch names (main/test) are correct

## Benefits of This Approach

### ✅ **Simplified Management**
- No version numbers to track
- Simple test/production flag based on device ID
- Automatic environment detection

### ✅ **Safe Testing**
- Production devices unaffected by test changes
- Test on specific devices before rollout
- Easy rollback (just revert Git commit)

### ✅ **Flexible Deployment**
- Add/remove test devices by rebuilding app
- Instant configuration updates via Git
- Gradual rollout by moving devices between environments

### ✅ **Git-Based Workflow**
- Version control for all configuration changes
- Merge test changes to production
- Full history and rollback capability

This approach gives you professional configuration management with the simplicity of a device ID flag system!
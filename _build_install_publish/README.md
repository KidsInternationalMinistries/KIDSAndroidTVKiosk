# Build, Install & Publish Scripts

This folder contains all scripts and documentation related to building, installing, and publishing the KIDS Android TV Kiosk application.

## 📁 Contents

### 🔧 Build Scripts
- **`build.sh`** - Shell script for building the Android application (Unix/Linux/WSL)

### � Version Management
- **`bump_version.ps1`** - Interactive version bumping tool that updates build.gradle and creates git branches

### �📱 Install Scripts
- **`debug_install.bat`** - Windows batch script for installing debug builds on connected Android devices
- **`debug_install.ps1`** - PowerShell script for installing debug builds with enhanced features and logging

### 🚀 Publishing Scripts
- **`git_publish_debug.ps1`** - Publishes changes to the test branch for testing and development
- **`git_publish_release.ps1`** - Creates production releases by merging test to main and creating version tags

### 📚 Documentation
- **`GIT-WORKFLOW.md`** - Complete guide to the Git workflow and publishing process

## 🎯 Quick Usage

### Version Management Workflow

1. **Bump version**: 
   ```powershell
   .\_build_install_publish\bump_version.ps1
   ```
   This will:
   - Show current version
   - Prompt for new version number
   - Update build.gradle automatically
   - Create version branch and commit changes

### Daily Development Workflow

1. **Make changes** to your code
2. **Test locally**: 
   ```powershell
   .\_build_install_publish\debug_install.ps1
   ```
3. **Publish for testing**: 
   ```powershell
   .\_build_install_publish\git_publish_debug.ps1 -Message "Your changes"
   ```

### Production Release Workflow

When ready for production:
```powershell
.\_build_install_publish\git_publish_release.ps1 -Version "v1.1.0" -Message "Release description"
```

## 📋 Script Details

### debug_install.ps1
**Purpose**: Build and install debug APK on connected Android TV device
**Features**:
- Builds the debug APK
- Installs on connected device
- Starts the main activity
- Provides helpful status messages
- Includes useful ADB commands for debugging

**Usage**:
```powershell
.\_build_install_publish\debug_install.ps1
```

### bump_version.ps1
**Purpose**: Interactive version management for Android app
**Features**:
- Displays current version from build.gradle
- Prompts for new version number with validation
- Auto-increments versionCode (required for Android)
- Creates version branch (e.g., version/v1.1)
- Commits changes automatically
- Shows next steps for testing and publishing

**Usage**:
```powershell
# Interactive mode (recommended)
.\_build_install_publish\bump_version.ps1

# Direct version specification
.\_build_install_publish\bump_version.ps1 -NewVersion "1.1"

# Help
.\_build_install_publish\bump_version.ps1 -Help
```

### git_publish_debug.ps1
**Purpose**: Commit and push changes to test branch
**Features**:
- Auto-cleans build files
- Commits only source code changes
- Pushes to GitHub test branch
- Interactive commit messages

**Usage**:
```powershell
.\_build_install_publish\git_publish_debug.ps1 -Message "Fixed UI issues"
```

### git_publish_release.ps1
**Purpose**: Create production releases
**Features**:
- Auto-commits any pending changes
- Merges test branch to main
- Creates version tags
- Handles merge conflicts automatically
- Pushes to GitHub with proper release tags

**Usage**:
```powershell
.\_build_install_publish\git_publish_release.ps1 -Version "v1.2.0" -Message "Added new features"
```

## 🔄 Usage

All scripts must be run from the project root directory using the relative path to this folder:

```powershell
# From project root directory
.\_build_install_publish\debug_install.ps1
.\_build_install_publish\git_publish_debug.ps1 -Message "Updates"
.\_build_install_publish\git_publish_release.ps1 -Version "v1.1.0"
```

### Daily Development Workflow

1. **Make changes** to your code
2. **Test locally**: 
   ```powershell
   .\_build_install_publish\debug_install.ps1
   ```
3. **Publish for testing**: 
   ```powershell
   .\_build_install_publish\git_publish_debug.ps1 -Message "Your changes"
   ```

### Production Release Workflow

When ready for production:
```powershell
.\_build_install_publish\git_publish_release.ps1 -Version "v1.1.0" -Message "Release description"
```

## 📖 For More Information

See `GIT-WORKFLOW.md` in this folder for detailed workflow documentation and examples.
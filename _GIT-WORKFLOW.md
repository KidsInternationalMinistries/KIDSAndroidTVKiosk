# Git Publishing Workflow

This project uses a simple Git workflow for development and deployment. Since you're the only developer, the process is streamlined for efficiency.

## Workflow Overview

1. **Development** - Work on the `test` branch
2. **Testing** - Use `_publish.ps1 debug` to commit and test changes
3. **Release** - Use `_publish.ps1 release` to create versioned releases

## Branch Structure

- **`test` branch** - Development and testing
- **`main` branch** - Production releases (via GitHub releases)

## Unified Publishing Script

### `_publish.ps1` - For Both Debug and Release

This unified script handles both debug and release publishing. You can specify the build type or let it prompt you interactively.

#### Debug Mode

**Use this when:** You've made changes and want to test them

**What it does:**
- Commits your changes to the `test` branch
- Builds debug APK
- Creates/updates "debug" pre-release on GitHub
- Keeps you on `test` branch for continued development

**Usage:**
```powershell
# Direct debug with custom message
.\_publish.ps1 debug -Message "Fixed UI bug in settings screen"

# Interactive mode (will ask for debug/release choice)
.\_publish.ps1
```

#### Release Mode

**Use this when:** You've tested everything and ready for production

**What it does:**
- Commits your changes to the `test` branch
- Builds release APK
- Extracts version from build.gradle (or uses provided version)
- Creates version tag (e.g., v1.3)
- Creates GitHub release with APK
- Keeps you on `test` branch for next development cycle

**Usage:**
```powershell
# Direct release with custom message (version auto-detected from build.gradle)
.\_publish.ps1 release -Message "Release v1.3 with new features"

# Interactive mode (will ask for debug/release choice)
.\_publish.ps1
```

## Typical Development Cycle

1. **Make changes** to your code
2. **Test locally** using `.\_debug_install.ps1`
3. **Publish for testing** using `.\_publish.ps1 debug`
4. **Continue development** or fix issues on `test` branch
5. **When ready for production** use `.\_publish.ps1 release`

## Examples

### Daily Development
```powershell
# Make some code changes...
# Test locally
.\_debug_install.ps1

# Commit and backup to GitHub
.\_publish.ps1 debug -Message "Added new configuration screen"

# Continue working...
```

### Creating a Release
```powershell
# Ensure everything is tested and working
.\_debug_install.ps1

# Final commit
.\_publish.ps1 debug -Message "Final testing complete for v1.3"

# Update version in build.gradle (if needed)
.\_bump_version.ps1

# Create production release
.\_publish.ps1 release -Message "Added device configuration and Google Sheets integration"
```

## Tips

- Always test with `.\_debug_install.ps1` before publishing
- Use descriptive commit messages
- Version numbers should follow semantic versioning (v1.0.0, v1.1.0, v2.0.0)
- The script automatically handles Git operations, so you don't need to worry about Git commands
- Use `.\_bump_version.ps1` to increment version numbers in build.gradle

## Safety Features

- Script checks that you're on the correct branch (`test`)
- Script verifies GitHub CLI authentication
- Build files are automatically excluded from commits
- Version is automatically extracted from build.gradle for releases
- Scripts provide confirmation prompts before major operations
# Git Publishing Workflow

This project uses a simple Git workflow for development and deployment. Since you're the only developer, the process is streamlined for efficiency.

## Workflow Overview

1. **Development** - Work on the `test` branch
2. **Testing** - Use `git_publish_debug.ps1` to commit and test changes
3. **Release** - Use `git_publish_release.ps1` to merge to main and create releases

## Branch Structure

- **`test` branch** - Development and testing
- **`main` branch** - Production releases

## Scripts

### 1. `git_publish_debug.ps1` - For Testing

**Use this when:** You've made changes and want to test them

**What it does:**
- Commits your changes to the `test` branch
- Pushes to GitHub for backup
- Keeps you on `test` branch for continued development

**Usage:**
```powershell
# With custom commit message
.\git_publish_debug.ps1 -Message "Fixed UI bug in settings screen"

# Interactive (will prompt for message)
.\git_publish_debug.ps1
```

### 2. `git_publish_release.ps1` - For Production

**Use this when:** You've tested everything and ready for production

**What it does:**
- Merges `test` branch into `main` branch
- Creates a version tag (e.g., v1.0.0)
- Pushes everything to GitHub
- Returns you to `test` branch for next development cycle

**Usage:**
```powershell
# With version and message
.\git_publish_release.ps1 -Version "v1.0.0" -Message "Initial release with kiosk functionality"

# Interactive (will prompt for version and message)
.\git_publish_release.ps1
```

## Typical Development Cycle

1. **Make changes** to your code
2. **Test locally** using `.\debug_install.ps1`
3. **Publish for testing** using `.\git_publish_debug.ps1`
4. **Continue development** or fix issues on `test` branch
5. **When ready for production** use `.\git_publish_release.ps1`

## Examples

### Daily Development
```powershell
# Make some code changes...
# Test locally
.\debug_install.ps1

# Commit and backup to GitHub
.\git_publish_debug.ps1 -Message "Added new configuration screen"

# Continue working...
```

### Creating a Release
```powershell
# Ensure everything is tested and working
.\debug_install.ps1

# Final commit
.\git_publish_debug.ps1 -Message "Final testing complete for v1.1.0"

# Create production release
.\git_publish_release.ps1 -Version "v1.1.0" -Message "Added device configuration and Google Sheets integration"
```

## Tips

- Always test with `debug_install.ps1` before publishing
- Use descriptive commit messages
- Version numbers should follow semantic versioning (v1.0.0, v1.1.0, v2.0.0)
- The scripts automatically handle Git operations, so you don't need to worry about Git commands

## Safety Features

- Scripts check that you're on the correct branch
- Scripts verify there are no uncommitted changes before releasing
- Build files are automatically excluded from commits
- Scripts provide confirmation prompts before major operations
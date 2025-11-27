package com.kidsim.tvkiosk.autostart;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.role.RoleManager;
import android.os.Build;
import java.util.List;

/**
 * Manages AutoStart functionality for the Kiosk app
 * Handles setting and removing the app as default launcher
 */
public class AutoStartManager {
    
    private static final String TAG = "AutoStartManager";
    private final Context context;
    private final PackageManager packageManager;
    
    // Component names for our launcher activities
    private static final String MAIN_ACTIVITY_CLASS = "com.kidsim.tvkiosk.MainActivity";
    private static final String HOME_ACTIVITY_ALIAS = "com.kidsim.tvkiosk.MainActivityHome";
    private static final String PACKAGE_NAME = "com.kidsim.tvkiosk";
    
    public AutoStartManager(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
    }
    
    /**
     * Interface for AutoStart operation callbacks
     */
    public interface AutoStartCallback {
        void onAutoStartEnabled();
        void onAutoStartDisabled();
        void onAutoStartError(String error);
    }
    
    /**
     * Check if the app is currently set as default launcher
     */
    public boolean isDefaultLauncher() {
        try {
            // First check if our HOME alias is enabled
            ComponentName homeAlias = new ComponentName(context, HOME_ACTIVITY_ALIAS);
            int aliasState = packageManager.getComponentEnabledSetting(homeAlias);
            
            if (aliasState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                Log.i(TAG, "HOME alias is disabled - not set as default launcher");
                return false;
            }
            
            // Check if we're in the default launcher list
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            
            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (PACKAGE_NAME.equals(resolveInfo.activityInfo.packageName)) {
                    Log.i(TAG, "App is currently set as default launcher");
                    return true;
                }
            }
            
            Log.i(TAG, "App is NOT set as default launcher");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking default launcher status", e);
            return false;
        }
    }
    
    /**
     * Enable AutoStart - set this app as default launcher
     */
    public void enableAutoStart(Activity activity, AutoStartCallback callback) {
        try {
            Log.i(TAG, "Enabling AutoStart - setting as default launcher");
            
            // Enable the HOME activity alias to appear in launcher selection
            ComponentName homeAlias = new ComponentName(context, HOME_ACTIVITY_ALIAS);
            
            packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );
            
            Log.i(TAG, "Enabled HOME launcher alias");
            
            // Clear any existing default launcher preference to force selection dialog
            try {
                packageManager.clearPackagePreferredActivities(PACKAGE_NAME);
                Log.i(TAG, "Cleared package preferred activities");
            } catch (Exception e) {
                Log.w(TAG, "Could not clear preferred activities: " + e.getMessage());
            }
            
            // Check if there are multiple launchers available
            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            
            if (resolveInfos.size() > 1) {
                // Multiple launchers available - show guidance dialog first
                Log.i(TAG, "Multiple launchers available - showing selection dialog");
                
                showLauncherSelectionGuidance(activity, callback);
                
            } else {
                // Only one launcher (ours) - already set as default
                Log.i(TAG, "App is now the only/default launcher");
                
                if (callback != null) {
                    callback.onAutoStartEnabled();
                }
            }
            
            // Try to programmatically set HOME role (Android 10+)
            trySetHomeRole(activity);
            
        } catch (Exception e) {
            Log.e(TAG, "Error enabling AutoStart", e);
            if (callback != null) {
                callback.onAutoStartError("Failed to enable AutoStart: " + e.getMessage());
            }
        }
    }
    
    /**
     * Show guidance dialog before launching the system launcher selection
     */
    private void showLauncherSelectionGuidance(Activity activity, AutoStartCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Enable AutoStart");
        builder.setMessage("To enable AutoStart:\n\n" +
                "You may see 1-2 dialogs:\n" +
                "1. Settings screen for launcher/home app selection\n" +
                "2. Permission request to set as HOME launcher\n\n" +
                "For each dialog:\n" +
                "• Look for 'KIDS Kiosk Bootstrap' in the list\n" +
                "• Select it and choose 'Always' or 'Allow'\n\n" +
                "This will make the app automatically start when the device boots.\n\n" +
                "Manual path: Settings > Apps > Default Apps > Home App");
        
        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "Opening launcher selection");
                forceLauncherSelection(activity);
                
                if (callback != null) {
                    callback.onAutoStartEnabled();
                }
            }
        });
        
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "User cancelled launcher selection");
                // Disable the alias since user cancelled
                ComponentName homeAlias = new ComponentName(context, HOME_ACTIVITY_ALIAS);
                packageManager.setComponentEnabledSetting(
                    homeAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                
                if (callback != null) {
                    callback.onAutoStartError("AutoStart setup cancelled by user");
                }
            }
        });
        
        builder.setCancelable(false);
        builder.show();
    }
    
    /**
     * Force the system to show launcher selection dialog
     */
    private void forceLauncherSelection(Activity activity) {
        // Try multiple methods for Android TV compatibility
        boolean success = false;
        
        // Method 1: Try Android TV Settings for Default Apps
        if (!success) {
            success = tryAndroidTVDefaultAppsSettings(activity);
        }
        
        // Method 2: Try generic settings for default apps
        if (!success) {
            success = tryGenericDefaultAppsSettings(activity);
        }
        
        // Method 3: Try role manager settings (newer Android)
        if (!success) {
            success = tryRoleManagerSettings(activity);
        }
        
        // Method 4: Use direct home intent (last resort)
        if (!success) {
            tryDirectHomeIntent(activity);
        }
    }
    
    private boolean tryAndroidTVDefaultAppsSettings(Activity activity) {
        try {
            // Android TV specific settings for default apps
            Intent settingsIntent = new Intent();
            settingsIntent.setAction("android.settings.HOME_SETTINGS");
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            activity.startActivity(settingsIntent);
            Log.i(TAG, "Opened Android TV home settings");
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Android TV home settings failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean tryGenericDefaultAppsSettings(Activity activity) {
        try {
            // Generic Android settings for default apps
            Intent settingsIntent = new Intent();
            settingsIntent.setAction("android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            activity.startActivity(settingsIntent);
            Log.i(TAG, "Opened default apps settings");
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Default apps settings failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean tryRoleManagerSettings(Activity activity) {
        try {
            // Try role manager for HOME launcher (Android 10+)
            Intent settingsIntent = new Intent();
            settingsIntent.setAction("android.settings.MANAGE_APP_ROLE");
            settingsIntent.putExtra("android.intent.extra.ROLE_NAME", "android.app.role.HOME");
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            activity.startActivity(settingsIntent);
            Log.i(TAG, "Opened role manager for HOME");
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Role manager settings failed: " + e.getMessage());
            return false;
        }
    }
    
    private void tryDirectHomeIntent(Activity activity) {
        try {
            // Last resort: Direct home intent with clear task
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            
            activity.startActivity(homeIntent);
            Log.i(TAG, "Opened direct home intent as fallback");
            
        } catch (Exception e) {
            Log.e(TAG, "All launcher selection methods failed", e);
        }
    }
    
    /**
     * Disable AutoStart - remove this app as default launcher
     */
    public void disableAutoStart(AutoStartCallback callback) {
        try {
            Log.i(TAG, "Disabling AutoStart - removing as default launcher");
            
            // Disable the HOME activity alias so it no longer appears as launcher option
            ComponentName homeAlias = new ComponentName(context, HOME_ACTIVITY_ALIAS);
            
            packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            
            Log.i(TAG, "Disabled HOME launcher alias");
            
            // Clear any default associations (this requires system permissions in newer Android)
            try {
                packageManager.clearPackagePreferredActivities(PACKAGE_NAME);
                Log.i(TAG, "Cleared package preferred activities");
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot clear preferred activities - insufficient permissions: " + e.getMessage());
            }
            
            // Force launcher selection to help user choose a new default
            try {
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(homeIntent);
                Log.i(TAG, "Opened launcher selection");
            } catch (Exception e) {
                Log.w(TAG, "Could not open launcher selection: " + e.getMessage());
            }
            
            Log.i(TAG, "AutoStart disabled successfully");
            
            if (callback != null) {
                callback.onAutoStartDisabled();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error disabling AutoStart", e);
            if (callback != null) {
                callback.onAutoStartError("Failed to disable AutoStart: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get current AutoStart status as a user-friendly string
     */
    public String getAutoStartStatus() {
        if (isDefaultLauncher()) {
            return "AutoStart Enabled - App is default launcher";
        } else {
            return "AutoStart Disabled - App is not default launcher";
        }
    }
    
    /**
     * Reset launcher settings to allow user to choose again
     * This opens the launcher selection dialog
     */
    public void resetLauncherSelection(Activity activity) {
        try {
            Log.i(TAG, "Resetting launcher selection");
            
            // Enable the HOME alias first
            ComponentName homeAlias = new ComponentName(context, HOME_ACTIVITY_ALIAS);
            packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );
            
            // Launch home intent to show selection
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            activity.startActivity(homeIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting launcher selection", e);
        }
    }
    
    /**
     * Try to programmatically set the HOME role for our app (Android 10+)
     */
    private void trySetHomeRole(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager roleManager = activity.getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    // Request HOME role for our package
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                    activity.startActivityForResult(intent, 1001);
                    Log.i(TAG, "Requested HOME role assignment");
                } else {
                    Log.w(TAG, "RoleManager or HOME role not available");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not request HOME role: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "RoleManager not available on this Android version");
        }
    }
    
    /**
     * Alternative method: Use shell commands to set default launcher (requires root/system)
     */
    @SuppressWarnings("unused")
    private void tryShellSetHomeRole() {
        try {
            // This would require root/system permissions - keeping for reference
            Runtime.getRuntime().exec("cmd role add-role-holder android.app.role.HOME " + PACKAGE_NAME);
            Log.i(TAG, "Attempted shell command to set HOME role");
        } catch (Exception e) {
            Log.w(TAG, "Shell command failed (expected without root): " + e.getMessage());
        }
    }
}
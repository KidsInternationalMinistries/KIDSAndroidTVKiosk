package com.kidsim.tvkiosk.autostart;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
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
            
            // First, enable our MainActivity as a launcher activity
            ComponentName mainActivity = new ComponentName(context, MAIN_ACTIVITY_CLASS);
            
            // Enable the component to appear in launcher selection
            packageManager.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );
            
            // Create home intent to trigger launcher selection
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Check if there are multiple launchers available
            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(homeIntent, 0);
            
            if (resolveInfos.size() > 1) {
                // Multiple launchers available - show selection dialog
                Log.i(TAG, "Multiple launchers available - showing selection dialog");
                activity.startActivity(homeIntent);
                
                if (callback != null) {
                    callback.onAutoStartEnabled();
                }
                
            } else {
                // Only one launcher (ours) - already set as default
                Log.i(TAG, "App is now the only/default launcher");
                
                if (callback != null) {
                    callback.onAutoStartEnabled();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error enabling AutoStart", e);
            if (callback != null) {
                callback.onAutoStartError("Failed to enable AutoStart: " + e.getMessage());
            }
        }
    }
    
    /**
     * Disable AutoStart - remove this app as default launcher
     */
    public void disableAutoStart(AutoStartCallback callback) {
        try {
            Log.i(TAG, "Disabling AutoStart - removing as default launcher");
            
            // Disable our MainActivity as a launcher activity
            ComponentName mainActivity = new ComponentName(context, MAIN_ACTIVITY_CLASS);
            
            // Disable the launcher component
            packageManager.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            
            // Clear any default associations (this requires system permissions in newer Android)
            try {
                // This may not work on all devices/Android versions without root
                packageManager.clearPackagePreferredActivities(PACKAGE_NAME);
                Log.i(TAG, "Cleared package preferred activities");
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot clear preferred activities - insufficient permissions: " + e.getMessage());
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
            
            // Enable our component first
            ComponentName mainActivity = new ComponentName(context, MAIN_ACTIVITY_CLASS);
            packageManager.setComponentEnabledSetting(
                mainActivity,
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
}
package com.kidsim.tvkiosk.autostart;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.List;

public class AutoStartManager {
    private static final String TAG = "AutoStartManager";
    private static final String PACKAGE_NAME = "com.kidsim.tvkiosk";
    private static final String MAIN_ACTIVITY_HOME = PACKAGE_NAME + ".MainActivityHome";
    
    public interface AutoStartCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public static boolean isDefaultLauncher(Context context) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                String defaultLauncherPackage = resolveInfo.activityInfo.packageName;
                Log.d(TAG, "Default launcher: " + defaultLauncherPackage);
                return PACKAGE_NAME.equals(defaultLauncherPackage);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking default launcher", e);
        }
        return false;
    }

    public static void enableAutoStart(Context context, AutoStartCallback callback) {
        try {
            Log.i(TAG, "Attempting to enable AutoStart...");
            
            // First verify the component is enabled (should be now with manifest fix)
            PackageManager pm = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, MAIN_ACTIVITY_HOME);
            
            int componentState = pm.getComponentEnabledSetting(componentName);
            Log.d(TAG, "Current component state: " + componentState);
            
            // If component is disabled, try to enable it
            if (componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                Log.i(TAG, "Enabling MainActivityHome component...");
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );
            }
            
            // Show user guidance dialog with enhanced instructions
            showEnhancedLauncherSelectionGuidance(context, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "Error enabling AutoStart", e);
            callback.onError("Failed to enable AutoStart: " + e.getMessage());
        }
    }

    public static void disableAutoStart(Context context, AutoStartCallback callback) {
        try {
            Log.i(TAG, "Disabling AutoStart...");
            
            // Clear any launcher preferences
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            
            context.getPackageManager().clearPackagePreferredActivities(PACKAGE_NAME);
            Log.i(TAG, "Cleared package preferred activities");
            
            callback.onSuccess("AutoStart disabled successfully. The system launcher will be used on next reboot.");
            
        } catch (Exception e) {
            Log.e(TAG, "Error disabling AutoStart", e);
            callback.onError("Failed to disable AutoStart: " + e.getMessage());
        }
    }

    private static void showEnhancedLauncherSelectionGuidance(Context context, AutoStartCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle("Enable AutoStart")
            .setMessage("To enable AutoStart:\n\n" +
                       "1. Press 'Continue' to open launcher selection\n" +
                       "2. Look for 'KIDS Kiosk Bootstrap' in the list\n" +
                       "3. Select it and choose 'Always' (not 'Just once')\n" +
                       "4. If no dialog appears, go to Settings > Apps > Default Apps > Home App manually\n\n" +
                       "Note: Some devices (like Xiaomi) may require manual setup in Settings.")
            .setPositiveButton("Continue", (dialog, which) -> {
                forceLauncherSelection(context, callback);
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                callback.onError("AutoStart setup cancelled by user");
            })
            .show();
    }

    private static void forceLauncherSelection(Context context, AutoStartCallback callback) {
        try {
            // Method 1: Try using Intent chooser (works on most devices)
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            Intent chooser = Intent.createChooser(homeIntent, "Select Default Launcher");
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(chooser);
            Log.i(TAG, "Launcher selection opened with chooser");
            
            // Give user time to make selection, then verify
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                verifyAutoStartSetup(context, callback);
            }, 3000);
            
        } catch (Exception e) {
            Log.w(TAG, "Chooser method failed, trying fallback", e);
            // Method 2: Fallback to direct home intent
            try {
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                
                context.startActivity(homeIntent);
                Log.i(TAG, "Launcher selection opened with direct intent");
                
                // Give user time to make selection, then verify
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    verifyAutoStartSetup(context, callback);
                }, 3000);
                
            } catch (Exception e2) {
                Log.e(TAG, "Both launcher selection methods failed", e2);
                callback.onError("Could not open launcher selection. Please go to Settings > Apps > Default Apps > Home App manually and select 'KIDS Kiosk Bootstrap'.");
            }
        }
    }

    private static void verifyAutoStartSetup(Context context, AutoStartCallback callback) {
        boolean isDefault = isDefaultLauncher(context);
        if (isDefault) {
            callback.onSuccess("AutoStart enabled successfully! The app will launch automatically on boot.");
        } else {
            callback.onError("AutoStart setup incomplete. If no dialog appeared, please go to Settings > Apps > Default Apps > Home App and select 'KIDS Kiosk Bootstrap' manually.");
        }
    }

    public static String getAutoStartStatus(Context context) {
        try {
            if (isDefaultLauncher(context)) {
                return "AutoStart Enabled - App is default launcher";
            } else {
                return "AutoStart Disabled - Use system launcher";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting AutoStart status", e);
            return "AutoStart Status Unknown";
        }
    }
}
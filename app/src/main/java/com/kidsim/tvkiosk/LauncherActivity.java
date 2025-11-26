package com.kidsim.tvkiosk;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

public class LauncherActivity extends Activity {
    private static final String TAG = "KioskLauncherActivity";
    private static final String PREFS_NAME = "KioskPrefs";
    private static final String LAST_LAUNCH_KEY = "last_launch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "=== KIOSK LAUNCHER ACTIVITY STARTED ===");
        
        // Track app launches for debugging
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong(LAST_LAUNCH_KEY, currentTime).apply();
        Log.i(TAG, "Launch tracked at: " + currentTime);
        
        // Start the AutoStartService first
        try {
            Intent serviceIntent = new Intent(this, AutoStartService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            Log.i(TAG, "AutoStartService started from LauncherActivity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AutoStartService", e);
        }
        
        // Start MainActivity with auto-start flag
        try {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mainIntent.putExtra("autoStart", true); // Flag to indicate auto-start
            startActivity(mainIntent);
            
            Log.i(TAG, "MainActivity started from LauncherActivity with auto-start flag");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MainActivity", e);
        }
        
        // Finish this launcher activity
        finish();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "LauncherActivity received new intent: " + intent.getAction());
        
        // Trigger a restart when we get a new intent
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish();
    }
}
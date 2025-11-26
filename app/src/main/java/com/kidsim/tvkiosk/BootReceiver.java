package com.kidsim.tvkiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "KioskBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "=== KIOSK BOOT RECEIVER TRIGGERED ===");
        Log.i(TAG, "Received broadcast: " + action);
        Log.i(TAG, "Intent extras: " + intent.getExtras());
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_USER_PRESENT.equals(action)) {
            
            Log.i(TAG, "Valid boot/unlock action detected: " + action);
            
            // Delay start to ensure system is fully booted
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startKioskApp(context);
                }
            }, 5000); // Wait 5 seconds after boot
            
            // Also try immediate start (in case delay doesn't work)
            startKioskApp(context);
        } else {
            Log.w(TAG, "Ignored action: " + action);
        }
    }
    
    private void startKioskApp(Context context) {
        try {
            Log.i(TAG, "Starting TV Kiosk app directly after boot...");
            
            // Start the main activity directly
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            context.startActivity(intent);
            Log.i(TAG, "TV Kiosk app started successfully!");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting TV Kiosk app after boot", e);
        }
    }
}
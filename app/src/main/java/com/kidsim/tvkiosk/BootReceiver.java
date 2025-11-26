package com.kidsim.tvkiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;
import android.content.ComponentName;

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
            Log.i(TAG, "Scheduling KioskJobService to start app...");
            
            // Schedule a job to start the app (more reliable than direct start)
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            
            JobInfo jobInfo = new JobInfo.Builder(1001, new ComponentName(context, KioskJobService.class))
                .setRequiresCharging(false)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresDeviceIdle(false)
                .setPersisted(true)  // Persist across reboots
                .setMinimumLatency(5000)  // Wait 5 seconds
                .setOverrideDeadline(10000)  // Must run within 10 seconds
                .build();
                
            int result = jobScheduler.schedule(jobInfo);
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "KioskJobService scheduled successfully!");
            } else {
                Log.e(TAG, "Failed to schedule KioskJobService");
            }
            
            // Also try direct service start as fallback
            Intent serviceIntent = new Intent(context, AutoStartService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            Log.i(TAG, "AutoStartService also started as fallback");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start services: " + e.getMessage(), e);
            
            // Final fallback: try to start the activity directly
            try {
                Intent startIntent = new Intent(context, MainActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                   Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                   Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(startIntent);
                Log.i(TAG, "Fallback: Started MainActivity directly");
            } catch (Exception e2) {
                Log.e(TAG, "All startup methods failed", e2);
            }
        }
    }
}
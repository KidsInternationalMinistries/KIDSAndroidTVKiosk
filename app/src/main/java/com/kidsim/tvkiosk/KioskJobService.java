package com.kidsim.tvkiosk;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class KioskJobService extends JobService {
    private static final String TAG = "KioskJobService";
    
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "=== KIOSK JOB SERVICE STARTED ===");
        
        try {
            // Start AutoStartService
            Intent serviceIntent = new Intent(this, AutoStartService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            Log.i(TAG, "AutoStartService started from JobService");
            
            // Try to start MainActivity
            Intent startIntent = new Intent(this, MainActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                               Intent.FLAG_ACTIVITY_CLEAR_TOP |
                               Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(startIntent);
            
            Log.i(TAG, "MainActivity start attempted from JobService");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start services from JobService", e);
        }
        
        // Job finished successfully
        jobFinished(params, false);
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "KioskJobService stopped");
        return false;
    }
}
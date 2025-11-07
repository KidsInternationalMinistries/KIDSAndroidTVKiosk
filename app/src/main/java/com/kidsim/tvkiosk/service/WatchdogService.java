package com.kidsim.tvkiosk.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.kidsim.tvkiosk.MainActivity;

public class WatchdogService extends Service {
    private static final String TAG = "WatchdogService";
    private static final long WATCHDOG_INTERVAL = 300000; // 5 minutes
    private static final long MAX_MEMORY_THRESHOLD = 150 * 1024 * 1024; // 150MB
    
    private Handler watchdogHandler;
    private Runnable watchdogRunnable;
    private boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "WatchdogService created");
        
        watchdogHandler = new Handler(Looper.getMainLooper());
        
        watchdogRunnable = new Runnable() {
            @Override
            public void run() {
                performHealthCheck();
                
                if (isRunning) {
                    watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL);
                }
            }
        };
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "WatchdogService started");
        startWatchdog();
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "WatchdogService destroyed");
        stopWatchdog();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding needed
    }
    
    private void startWatchdog() {
        if (!isRunning) {
            isRunning = true;
            watchdogHandler.post(watchdogRunnable);
            Log.d(TAG, "Watchdog started");
        }
    }
    
    private void stopWatchdog() {
        isRunning = false;
        if (watchdogHandler != null && watchdogRunnable != null) {
            watchdogHandler.removeCallbacks(watchdogRunnable);
        }
        Log.d(TAG, "Watchdog stopped");
    }
    
    private void performHealthCheck() {
        try {
            // Check memory usage
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long availableMemory = maxMemory - usedMemory;
            
            Log.d(TAG, String.format("Memory: Used=%dMB, Available=%dMB, Max=%dMB", 
                usedMemory / (1024 * 1024), 
                availableMemory / (1024 * 1024), 
                maxMemory / (1024 * 1024)));
            
            // If memory usage is too high, suggest garbage collection
            if (usedMemory > MAX_MEMORY_THRESHOLD) {
                Log.w(TAG, "High memory usage detected, suggesting GC");
                System.gc();
                
                // If still high after GC, consider restarting the app
                runtime = Runtime.getRuntime();
                usedMemory = runtime.totalMemory() - runtime.freeMemory();
                
                if (usedMemory > MAX_MEMORY_THRESHOLD) {
                    Log.e(TAG, "Memory usage still high after GC, considering app restart");
                    restartMainActivity();
                }
            }
            
            // Check if MainActivity is responsive (basic check)
            // This is a simple implementation - could be enhanced with ping/pong mechanism
            
        } catch (Exception e) {
            Log.e(TAG, "Error during health check", e);
        }
    }
    
    private void restartMainActivity() {
        try {
            Log.i(TAG, "Restarting MainActivity due to health check failure");
            
            Intent restartIntent = new Intent(this, MainActivity.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                 Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                 Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            startActivity(restartIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart MainActivity", e);
        }
    }
}
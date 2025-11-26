package com.kidsim.tvkiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class AutoStartService extends Service {
    private static final String TAG = "KioskAutoStartService";
    private static final String CHANNEL_ID = "kiosk_autostart";
    private static final int NOTIFICATION_ID = 1001;
    
    private Handler handler;
    private Runnable startAppRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "=== KIOSK AUTO-START SERVICE CREATED ===");
        
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        
        // Create runnable to start the main app
        startAppRunnable = new Runnable() {
            @Override
            public void run() {
                startKioskApp();
                // Keep trying every 30 seconds until app is running
                handler.postDelayed(this, 30000);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Auto-start service started");
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Start trying to launch the app after 3 seconds
        handler.postDelayed(startAppRunnable, 3000);
        
        // Return START_STICKY to restart if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Auto-start service destroyed");
        if (handler != null && startAppRunnable != null) {
            handler.removeCallbacks(startAppRunnable);
        }
    }

    private void startKioskApp() {
        try {
            Log.i(TAG, "Attempting to start MainActivity...");
            
            Intent startIntent = new Intent(this, MainActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                               Intent.FLAG_ACTIVITY_CLEAR_TOP |
                               Intent.FLAG_ACTIVITY_SINGLE_TOP |
                               Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            
            startActivity(startIntent);
            Log.i(TAG, "MainActivity start command sent");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MainActivity: " + e.getMessage(), e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Kiosk Auto-Start",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps kiosk app running automatically");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kiosk Auto-Start")
            .setContentText("Keeping kiosk running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
}
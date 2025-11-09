package com.kidsim.tvkiosk.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class DeviceIdManager {
    private static final String TAG = "DeviceIdManager";
    private static final String PREFS_NAME = "DeviceConfig";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_IS_DEBUG_MODE = "is_debug_mode";
    
    private Context context;
    private SharedPreferences prefs;
    
    public DeviceIdManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Check if device ID has been configured
     */
    public boolean isDeviceIdConfigured() {
        String deviceId = getDeviceId();
        String orientation = prefs.getString("orientation", null);
        return deviceId != null && !deviceId.isEmpty() && orientation != null && !orientation.isEmpty();
    }
    
    /**
     * Get the stored device ID
     */
    public String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, null);
    }
    
    /**
     * Set the device ID
     */
    public void setDeviceId(String deviceId) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply();
        Log.i(TAG, "Device ID set to: " + deviceId);
    }
    
    /**
     * Get the stored orientation
     */
    public String getOrientation() {
        return prefs.getString("orientation", "landscape");
    }
    
    /**
     * Set the orientation
     */
    public void setOrientation(String orientation) {
        prefs.edit()
            .putString("orientation", orientation.toLowerCase())
            .apply();
        Log.i(TAG, "Orientation set to: " + orientation);
    }
    
    /**
     * Check if app is in debug mode
     */
    public boolean isDebugMode() {
        return prefs.getBoolean(KEY_IS_DEBUG_MODE, false);
    }
}
package com.kidsim.tvkiosk.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigurationManager {
    private static final String TAG = "ConfigurationManager";
    private static final String PREFS_NAME = "KioskConfig";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_LAST_UPDATE = "last_update";
    
    // GitHub raw URLs - update with your actual repository
    private static final String PRODUCTION_CONFIG_URL = 
        "https://raw.githubusercontent.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/main/config.json";
    private static final String TEST_CONFIG_URL = 
        "https://raw.githubusercontent.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/test/config.json";
    
    private Context context;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private ConfigUpdateListener listener;
    
    public interface ConfigUpdateListener {
        void onConfigUpdated(DeviceConfig config);
        void onConfigError(String error);
    }
    
    public ConfigurationManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public void setConfigUpdateListener(ConfigUpdateListener listener) {
        this.listener = listener;
    }
    
    public DeviceConfig getCurrentConfig() {
        String configJson = prefs.getString(KEY_CONFIG_JSON, null);
        if (configJson != null) {
            try {
                return parseConfig(configJson);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse stored config", e);
            }
        }
        return getDefaultConfig();
    }
    
    public void updateConfigFromGitHub(String configUrl) {
        // If no URL provided, determine based on device test flag
        final String finalConfigUrl;
        if (configUrl == null || configUrl.isEmpty()) {
            finalConfigUrl = shouldUseTestConfig() ? TEST_CONFIG_URL : PRODUCTION_CONFIG_URL;
            Log.i(TAG, "Using " + (shouldUseTestConfig() ? "TEST" : "PRODUCTION") + " configuration for device: " + getDeviceId());
        } else {
            finalConfigUrl = configUrl;
        }
        
        executor.execute(() -> {
            try {
                String configJson = downloadConfig(finalConfigUrl);
                DeviceConfig config = parseConfig(configJson);
                
                // Filter config for this device
                DeviceConfig deviceConfig = getConfigForThisDevice(config);
                
                // Save to preferences
                prefs.edit()
                    .putString(KEY_CONFIG_JSON, configJson)
                    .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                    .apply();
                
                if (listener != null) {
                    listener.onConfigUpdated(deviceConfig);
                }
                
                Log.i(TAG, "Configuration updated successfully from " + finalConfigUrl);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update configuration from " + finalConfigUrl, e);
                if (listener != null) {
                    listener.onConfigError("Failed to update configuration: " + e.getMessage());
                }
            }
        });
    }
    
    private String downloadConfig(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "KioskTV-Android");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode);
            }
            
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            return response.toString();
            
        } finally {
            connection.disconnect();
        }
    }
    
    private DeviceConfig parseConfig(String configJson) throws JSONException {
        JSONObject json = new JSONObject(configJson);
        
        // Check if this is a multi-device config or single device config
        if (json.has("devices")) {
            // Multi-device configuration
            JSONArray devices = json.getJSONArray("devices");
            String deviceId = getDeviceId();
            
            for (int i = 0; i < devices.length(); i++) {
                JSONObject deviceJson = devices.getJSONObject(i);
                if (deviceId.equals(deviceJson.optString("deviceId"))) {
                    return parseDeviceConfig(deviceJson);
                }
            }
            
            // If no specific config found, use default or first one
            if (devices.length() > 0) {
                JSONObject defaultDevice = devices.getJSONObject(0);
                DeviceConfig config = parseDeviceConfig(defaultDevice);
                config.setDeviceId(deviceId); // Override with actual device ID
                return config;
            }
        }
        
        // Single device configuration
        return parseDeviceConfig(json);
    }
    
    private DeviceConfig parseDeviceConfig(JSONObject json) throws JSONException {
        DeviceConfig config = new DeviceConfig();
        
        config.setDeviceId(json.optString("deviceId", getDeviceId()));
        config.setDeviceName(json.optString("deviceName", "Android TV"));
        config.setOrientation(json.optString("orientation", "landscape"));
        config.setRefreshIntervalMinutes(json.optInt("refreshIntervalMinutes", 60));
        config.setAutoStart(json.optBoolean("autoStart", true));
        config.setClearCache(json.optBoolean("clearCache", false));
        config.setConfigVersion(json.optString("configVersion", "1.0"));
        
        // Parse pages
        List<PageConfig> pages = new ArrayList<>();
        if (json.has("pages")) {
            JSONArray pagesArray = json.getJSONArray("pages");
            for (int i = 0; i < pagesArray.length(); i++) {
                JSONObject pageJson = pagesArray.getJSONObject(i);
                PageConfig page = new PageConfig();
                page.setUrl(pageJson.getString("url"));
                page.setDisplayTimeSeconds(pageJson.optInt("displayTimeSeconds", 300));
                page.setTitle(pageJson.optString("title", ""));
                pages.add(page);
            }
        } else if (json.has("url")) {
            // Single URL configuration for backward compatibility
            PageConfig page = new PageConfig();
            page.setUrl(json.getString("url"));
            page.setDisplayTimeSeconds(config.getRefreshIntervalMinutes() * 60);
            pages.add(page);
        }
        
        config.setPages(pages);
        return config;
    }
    
    private DeviceConfig getConfigForThisDevice(DeviceConfig globalConfig) {
        // For now, just return the config as-is
        // In the future, this could filter based on device capabilities, etc.
        return globalConfig;
    }
    
    private DeviceConfig getDefaultConfig() {
        DeviceConfig config = new DeviceConfig();
        config.setDeviceId(getDeviceId());
        config.setDeviceName("Android TV Kiosk");
        
        // Default page configuration
        List<PageConfig> pages = new ArrayList<>();
        pages.add(new PageConfig("https://sponsor.kidsim.org", 3600)); // 1 hour
        config.setPages(pages);
        
        return config;
    }
    
    private String getDeviceId() {
        try {
            // Use Android ID as device identifier
            String androidId = Settings.Secure.getString(context.getContentResolver(), 
                Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                return androidId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get Android ID", e);
        }
        
        // Fallback to device model and serial
        return Build.MODEL + "_" + Build.SERIAL;
    }
    
    private boolean shouldUseTestConfig() {
        String deviceId = getDeviceId();
        
        // Add test device IDs here - devices that should use test configuration
        String[] testDeviceIds = {
            // Add your test device IDs here
            // "abc123def456",     // Your development device
            // "test_device_01",   // Test TV #1
            // "test_device_02"    // Test TV #2
        };
        
        for (String testId : testDeviceIds) {
            if (deviceId.equals(testId)) {
                Log.i(TAG, "Device " + deviceId + " flagged for TEST configuration");
                return true;
            }
        }
        
        Log.i(TAG, "Device " + deviceId + " using PRODUCTION configuration");
        return false;
    }
    
    public long getLastUpdateTime() {
        return prefs.getLong(KEY_LAST_UPDATE, 0);
    }
    
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
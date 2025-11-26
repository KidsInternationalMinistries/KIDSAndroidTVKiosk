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
    private GoogleSheetsConfigLoader sheetsLoader;
    private DeviceIdManager deviceIdManager;
    
    public interface ConfigUpdateListener {
        void onConfigUpdated(DeviceConfig config);
        void onConfigError(String error);
    }
    
    public ConfigurationManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.deviceIdManager = new DeviceIdManager(context);
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
                JSONObject configObj = new JSONObject(configJson);
                
                // Check if this configuration uses Google Sheets
                String configSource = configObj.optString("configSource", "json");
                if ("googleSheets".equals(configSource)) {
                    Log.i(TAG, "Google Sheets configuration detected, loading from sheets");
                    loadConfigFromGoogleSheets(configObj);
                } else {
                    // Traditional JSON configuration
                    Log.i(TAG, "JSON configuration detected, parsing directly");
                    DeviceConfig config = parseConfig(configJson);
                    DeviceConfig deviceConfig = getConfigForThisDevice(config);
                    
                    // Save to preferences
                    prefs.edit()
                        .putString(KEY_CONFIG_JSON, configJson)
                        .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                        .apply();
                    
                    if (listener != null) {
                        listener.onConfigUpdated(deviceConfig);
                    }
                    
                    Log.i(TAG, "JSON Configuration updated successfully from " + finalConfigUrl);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update configuration from " + finalConfigUrl, e);
                if (listener != null) {
                    listener.onConfigError("Failed to update configuration: " + e.getMessage());
                }
            }
        });
    }
    
    private void loadConfigFromGoogleSheets(JSONObject baseConfig) {
        try {
            // Get device ID from DeviceIdManager first, fallback to device name
            String tempDeviceId = deviceIdManager.getDeviceId();
            final String deviceId;
            if (tempDeviceId == null || tempDeviceId.isEmpty()) {
                deviceId = getDeviceName(); // Fallback to device name for sheet matching
                Log.i(TAG, "No configured device ID, using device name: " + deviceId);
            } else {
                deviceId = tempDeviceId;
                Log.i(TAG, "Using configured device ID: " + deviceId);
            }
            
            // Initialize Google Sheets loader if not already done
            if (sheetsLoader == null) {
                // Get API key and sheets ID from configuration
                String apiKey = baseConfig.getString("googleSheetsApiKey");
                String sheetsId = baseConfig.getString("googleSheetsId");
                
                if (apiKey == null || apiKey.isEmpty() || sheetsId == null || sheetsId.isEmpty()) {
                    throw new IllegalArgumentException("Missing API key or sheets ID for Google Sheets API");
                }
                
                Log.i(TAG, "Using Google Sheets API v4 method");
                sheetsLoader = new GoogleSheetsConfigLoader(sheetsId, apiKey);
            }
            
            Log.i(TAG, "Loading configuration for device: " + deviceId + " from Google Sheets");
            
            sheetsLoader.loadDeviceConfig(deviceId, new GoogleSheetsConfigLoader.ConfigLoadListener() {
                @Override
                public void onConfigLoaded(DeviceConfig config) {
                    try {
                        // Save the base config and successful load time
                        prefs.edit()
                            .putString(KEY_CONFIG_JSON, baseConfig.toString())
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply();
                        
                        if (listener != null) {
                            listener.onConfigUpdated(config);
                        }
                        
                        Log.i(TAG, "Google Sheets configuration loaded successfully for device: " + deviceId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing loaded config", e);
                        loadFallbackConfig(baseConfig);
                    }
                }
                
                @Override
                public void onConfigLoadFailed(String error) {
                    Log.w(TAG, "Failed to load from Google Sheets: " + error);
                    loadFallbackConfig(baseConfig);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Google Sheets loader", e);
            loadFallbackConfig(baseConfig);
        }
    }
    
    private void loadFallbackConfig(JSONObject baseConfig) {
        try {
            JSONObject fallbackConfig = baseConfig.getJSONObject("fallbackConfig");
            DeviceConfig config = parseDeviceConfigFromJson(fallbackConfig);
            config.setDeviceId(getDeviceId());
            
            // Use configured device ID or fallback to device name
            String deviceName = deviceIdManager.getDeviceId();
            if (deviceName == null || deviceName.isEmpty()) {
                deviceName = getDeviceName();
            }
            config.setDeviceName(deviceName);
            
            if (listener != null) {
                listener.onConfigUpdated(config);
            }
            
            Log.i(TAG, "Using fallback configuration");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load fallback config", e);
            if (listener != null) {
                listener.onConfigError("Failed to load any configuration");
            }
        }
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
        return parseDeviceConfigFromJson(json);
    }
    
    private DeviceConfig parseDeviceConfigFromJson(JSONObject json) throws JSONException {
        DeviceConfig config = new DeviceConfig();
        
        config.setDeviceId(json.optString("deviceId", getDeviceId()));
        config.setDeviceName(json.optString("deviceName", "Android TV"));
        config.setOrientation(json.optString("orientation", "landscape"));
        config.setRefreshIntervalMinutes(json.optInt("refreshIntervalMinutes", 60));
        // autoStart and clearCache fields removed - not used in simplified kiosk
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
                // Title field removed - not used in kiosk display
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
    
    private String getDeviceName() {
        try {
            // Try to get device name from settings first
            String deviceName = Settings.Global.getString(context.getContentResolver(), "device_name");
            if (deviceName != null && !deviceName.isEmpty()) {
                Log.i(TAG, "Using device name from settings: " + deviceName);
                return deviceName;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device name from settings", e);
        }
        
        try {
            // Fallback to Bluetooth device name
            String deviceName = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
            if (deviceName != null && !deviceName.isEmpty()) {
                Log.i(TAG, "Using Bluetooth device name: " + deviceName);
                return deviceName;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get Bluetooth device name", e);
        }
        
        // Final fallback to Build.MODEL
        String fallbackName = Build.MODEL;
        Log.i(TAG, "Using device model as name: " + fallbackName);
        return fallbackName;
    }
    
    private boolean shouldUseTestConfig() {
        String deviceId = getDeviceId();
        
        // Log device ID for debugging
        Log.i(TAG, "=== DEVICE ID DEBUG ===");
        Log.i(TAG, "Current Device ID: " + deviceId);
        Log.i(TAG, "======================");
        
        // Add test device IDs here - devices that should use test configuration
        String[] testDeviceIds = {
            "test",                 // Your test Android TV device
            "382a9b8d8e53e5df",     // Your phone device ID (actual from logs)
            "bd97668be0c1ef6e",     // Your phone device ID (from ADB command)
            "33021JEHN03011",       // Your phone ADB serial (backup)
            // "abc123def456",      // Add more test device IDs here
            // "test_device_01",    // Test TV #1
            // "test_device_02"     // Test TV #2
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
    
    public String getGoogleSheetsApiKey() {
        try {
            JSONObject config = loadLocalConfigFromAssets();
            if (config != null && config.has("googleSheetsApiKey")) {
                return config.getString("googleSheetsApiKey");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Google Sheets API key", e);
        }
        return null;
    }
    
    public String getGoogleSheetsId() {
        try {
            JSONObject config = loadLocalConfigFromAssets();
            if (config != null && config.has("googleSheetsId")) {
                return config.getString("googleSheetsId");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Google Sheets ID", e);
        }
        return null;
    }
    
    private JSONObject loadLocalConfigFromAssets() {
        try {
            InputStream inputStream = context.getAssets().open("config.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();
            return new JSONObject(jsonString.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error reading config.json from assets", e);
            return null;
        }
    }
    
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
        if (sheetsLoader != null) {
            sheetsLoader.shutdown();
        }
    }
}
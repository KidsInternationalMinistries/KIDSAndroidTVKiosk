package com.kidsim.tvkiosk.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import com.kidsim.tvkiosk.utils.ErrorHandler;

public class ConfigurationManager {
    private static final String TAG = "ConfigurationManager";
    private static final String PREFS_NAME = "KioskConfig";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_LAST_UPDATE = "last_update";
    
    // Google Sheets API v4 configuration
    private static final String GOOGLE_SHEETS_ID = "1vWzoYpMDIwfpAuwChbwinmoZxqwelO64ODOS97b27ag";
    private static final String GOOGLE_SHEETS_API_KEY = "AIzaSyBAqxngnd1msF_2eUiW-cP-Ab9DoRrztt4";
    
    private Context context;
    private SharedPreferences prefs;
    private ConfigUpdateListener listener;
    private GoogleSheetsConfigLoader sheetsLoader;
    
    public interface ConfigUpdateListener {
        void onConfigUpdated(DeviceConfig config);
        void onConfigError(String error);
    }
    
    public ConfigurationManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.sheetsLoader = new GoogleSheetsConfigLoader(GOOGLE_SHEETS_ID, GOOGLE_SHEETS_API_KEY);
    }
    private DeviceConfig currentConfig; // Store config in memory only
    
    public void setConfigUpdateListener(ConfigUpdateListener listener) {
        this.listener = listener;
    }
    
    public DeviceConfig getCurrentConfig() {
        // Return in-memory config only, no persistent storage
        return currentConfig;
    }
    
    public String getGoogleSheetsApiKey() {
        return GOOGLE_SHEETS_API_KEY;
    }
    
    public String getGoogleSheetsId() {
        return GOOGLE_SHEETS_ID;
    }
    
    public void loadConfigurationFromGoogleSheets(String deviceId) {
        if (sheetsLoader == null) {
            sheetsLoader = new GoogleSheetsConfigLoader(GOOGLE_SHEETS_ID, GOOGLE_SHEETS_API_KEY);
        }
        
        Log.i(TAG, "Loading configuration for device: " + deviceId + " from Google Sheets");
        
        sheetsLoader.loadDeviceConfig(deviceId, new GoogleSheetsConfigLoader.ConfigLoadListener() {
            @Override
            public void onConfigLoaded(DeviceConfig config) {
                try {
                    // Store config in memory only, no persistent storage
                    currentConfig = config;
                    
                    if (listener != null) {
                        listener.onConfigUpdated(config);
                    }
                    
                    Log.i(TAG, "Google Sheets configuration loaded successfully for device: " + deviceId);
                    
                } catch (Exception e) {
                    ErrorHandler.logErrorWithCallback(TAG, "Failed to process Google Sheets configuration", e, 
                        listener != null ? error -> listener.onConfigError("Failed to process configuration: " + e.getMessage()) : null);
                }
            }
            
            @Override
            public void onConfigLoadFailed(String error) {
                ErrorHandler.logErrorWithCallback(TAG, "Failed to load configuration from Google Sheets: " + error, 
                    listener != null ? err -> listener.onConfigError("Failed to load configuration: " + error) : null);
            }
        });
    }
    
    private String serializeConfigToJson(DeviceConfig config) throws Exception {
        // Simple JSON serialization for device config
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"deviceName\":\"").append(config.getDeviceName()).append("\",");
        json.append("\"orientation\":\"").append(config.getOrientation()).append("\",");
        json.append("\"refreshMinutes\":").append(config.getRefreshIntervalMinutes()).append(",");
        json.append("\"pages\":[");
        
        List<PageConfig> pages = config.getPages();
        for (int i = 0; i < pages.size(); i++) {
            PageConfig page = pages.get(i);
            json.append("{");
            json.append("\"url\":\"").append(page.getUrl()).append("\",");
            json.append("\"displaySeconds\":").append(page.getDisplayTimeSeconds());
            json.append("}");
            if (i < pages.size() - 1) {
                json.append(",");
            }
        }
        
        json.append("]");
        json.append("}");
        return json.toString();
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
                    return parseDeviceConfigFromJson(deviceJson);
                }
            }
            
            // If no specific config found, use default or first one
            if (devices.length() > 0) {
                JSONObject defaultDevice = devices.getJSONObject(0);
                DeviceConfig config = parseDeviceConfigFromJson(defaultDevice);
                config.setDeviceId(deviceId); // Override with actual device ID
                return config;
            }
        }
        
        // Single device configuration
        return parseDeviceConfigFromJson(json);
    }
    
    private DeviceConfig parseDeviceConfigFromJson(JSONObject json) throws JSONException {
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

    
    public long getLastUpdateTime() {
        return prefs.getLong(KEY_LAST_UPDATE, 0);
    }
    
    public void shutdown() {
        if (sheetsLoader != null) {
            sheetsLoader.shutdown();
        }
    }
}
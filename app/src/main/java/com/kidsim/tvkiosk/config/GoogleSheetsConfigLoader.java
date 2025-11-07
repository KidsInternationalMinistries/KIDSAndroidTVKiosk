package com.kidsim.tvkiosk.config;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleSheetsConfigLoader {
    private static final String TAG = "GoogleSheetsLoader";
    private final String baseUrl;
    private final ExecutorService executor;
    
    public interface ConfigLoadListener {
        void onConfigLoaded(DeviceConfig config);
        void onConfigLoadFailed(String error);
    }
    
    public GoogleSheetsConfigLoader(String baseUrl) {
        this.baseUrl = baseUrl;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public void loadDeviceConfig(String deviceId, ConfigLoadListener listener) {
        executor.execute(() -> {
            try {
                // First, try to get the sheet list to find the correct gid for the device
                String deviceGid = findDeviceSheetGid(deviceId);
                if (deviceGid == null) {
                    listener.onConfigLoadFailed("Device sheet not found: " + deviceId);
                    return;
                }
                
                // Load the device-specific sheet
                DeviceConfig config = loadDeviceSheet(deviceId, deviceGid);
                if (config != null) {
                    listener.onConfigLoaded(config);
                } else {
                    listener.onConfigLoadFailed("Failed to parse device configuration");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading config from Google Sheets", e);
                listener.onConfigLoadFailed("Network error: " + e.getMessage());
            }
        });
    }
    
    private String findDeviceSheetGid(String deviceId) {
        // For now, we'll use a simple mapping approach
        // Later we can implement automatic sheet discovery
        switch (deviceId.toLowerCase()) {
            case "test":
                return "0"; // First sheet (usually gid=0)
            case "production":
                return "1"; // Second sheet
            case "lobby":
                return "2"; // Third sheet
            default:
                // Try the default sheet (gid=0)
                return "0";
        }
    }
    
    private DeviceConfig loadDeviceSheet(String deviceId, String gid) {
        try {
            String csvUrl = baseUrl + gid;
            Log.d(TAG, "Loading device config from: " + csvUrl);
            
            URL url = new URL(csvUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            List<String> csvLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                csvLines.add(line);
            }
            reader.close();
            connection.disconnect();
            
            return parseDeviceConfigFromCsv(deviceId, csvLines);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading device sheet", e);
            return null;
        }
    }
    
    private DeviceConfig parseDeviceConfigFromCsv(String deviceId, List<String> csvLines) {
        try {
            if (csvLines.size() < 3) {
                Log.e(TAG, "CSV too short, expected at least 3 lines");
                return null;
            }
            
            // Parse configuration from CSV structure:
            // Row 1: Device Name, Orientation
            // Row 2: (empty), Refresh Interval Minutes
            // Row 3: (empty)
            // Row 5+: URL, Display Time Seconds
            
            String[] row1 = parseCsvLine(csvLines.get(0));
            String[] row2 = parseCsvLine(csvLines.get(1));
            
            String deviceName = row1.length > 0 ? row1[0] : deviceId;
            String orientation = row1.length > 1 ? row1[1] : "landscape";
            int refreshInterval = 5; // default
            
            if (row2.length > 1) {
                try {
                    refreshInterval = Integer.parseInt(row2[1].trim());
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid refresh interval, using default: " + row2[1]);
                }
            }
            
            // Parse pages starting from row 5 (index 4)
            List<PageConfig> pages = new ArrayList<>();
            for (int i = 4; i < csvLines.size(); i++) {
                String[] pageRow = parseCsvLine(csvLines.get(i));
                if (pageRow.length >= 2 && !pageRow[0].trim().isEmpty()) {
                    String pageUrl = pageRow[0].trim();
                    int displayTime = 30; // default
                    
                    try {
                        displayTime = Integer.parseInt(pageRow[1].trim());
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid display time, using default: " + pageRow[1]);
                    }
                    
                    PageConfig pageConfig = new PageConfig(pageUrl, displayTime);
                    pageConfig.setTitle("Page " + (pages.size() + 1));
                    pages.add(pageConfig);
                }
            }
            
            if (pages.isEmpty()) {
                Log.e(TAG, "No pages found in configuration");
                return null;
            }
            
            Log.i(TAG, "Loaded config for " + deviceName + " with " + pages.size() + " pages");
            
            DeviceConfig config = new DeviceConfig();
            config.setDeviceId(deviceId);
            config.setDeviceName(deviceName);
            config.setOrientation(orientation);
            config.setRefreshIntervalMinutes(refreshInterval);
            config.setAutoStart(true);
            config.setClearCache(true);
            config.setPages(pages);
            
            return config;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CSV config", e);
            return null;
        }
    }
    
    private String[] parseCsvLine(String line) {
        // Simple CSV parsing - handle quoted fields and commas
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
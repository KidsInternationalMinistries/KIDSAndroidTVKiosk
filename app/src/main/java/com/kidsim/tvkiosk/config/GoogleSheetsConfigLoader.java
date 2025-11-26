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
    private final String sheetsId;
    private final String apiKey;
    private final ExecutorService executor;
    
    // Google Sheets API v4 endpoints
    private static final String SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets/";
    
    public interface ConfigLoadListener {
        void onConfigLoaded(DeviceConfig config);
        void onConfigLoadFailed(String error);
    }
    
    public GoogleSheetsConfigLoader(String sheetsId, String apiKey) {
        this.sheetsId = sheetsId;
        this.apiKey = apiKey;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public void loadDeviceConfig(String deviceId, ConfigLoadListener listener) {
        executor.execute(() -> {
            try {
                // First, get the list of sheets to find the correct sheet for the device
                String sheetName = findDeviceSheet(deviceId);
                if (sheetName == null) {
                    listener.onConfigLoadFailed("Device sheet not found: " + deviceId);
                    return;
                }
                
                // Load the device configuration from the found sheet
                DeviceConfig config = loadConfigFromSheet(deviceId, sheetName);
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
    
    public interface DeviceListListener {
        void onDeviceListLoaded(List<String> deviceIds);
        void onDeviceListFailed(String error);
    }
    
    public void loadAvailableDeviceIds(DeviceListListener listener) {
        executor.execute(() -> {
            try {
                List<String> deviceIds = getDeviceIdsFromSheet();
                if (deviceIds != null && !deviceIds.isEmpty()) {
                    listener.onDeviceListLoaded(deviceIds);
                } else {
                    listener.onDeviceListFailed("No device IDs found in sheet");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading device IDs", e);
                listener.onDeviceListFailed("Network error: " + e.getMessage());
            }
        });
    }
    
    private List<String> getDeviceIdsFromSheet() {
        try {
            // Get all sheet names from the spreadsheet - these are the device IDs
            String url = SHEETS_API_BASE + sheetsId + "?key=" + apiKey;
            
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API HTTP error: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            
            // Parse the response to get all sheet names as device IDs
            org.json.JSONObject spreadsheet = new org.json.JSONObject(response.toString());
            org.json.JSONArray sheets = spreadsheet.getJSONArray("sheets");
            
            List<String> deviceIds = new ArrayList<>();
            
            // Extract sheet names as device IDs
            for (int i = 0; i < sheets.length(); i++) {
                org.json.JSONObject sheet = sheets.getJSONObject(i);
                org.json.JSONObject properties = sheet.getJSONObject("properties");
                String sheetTitle = properties.getString("title");
                
                Log.d(TAG, "Found device ID from tab: " + sheetTitle);
                deviceIds.add(sheetTitle);
            }
            
            Log.i(TAG, "Found " + deviceIds.size() + " device IDs from tab names");
            return deviceIds;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting device IDs from sheet tabs", e);
            return null;
        }
    }
    
    private String getFirstSheetName() {
        try {
            String url = SHEETS_API_BASE + sheetsId + "?key=" + apiKey;
            
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            
            org.json.JSONObject spreadsheet = new org.json.JSONObject(response.toString());
            org.json.JSONArray sheets = spreadsheet.getJSONArray("sheets");
            
            if (sheets.length() > 0) {
                org.json.JSONObject sheet = sheets.getJSONObject(0);
                org.json.JSONObject properties = sheet.getJSONObject("properties");
                return properties.getString("title");
            }
            
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting first sheet name", e);
            return null;
        }
    }
    
    private String findDeviceSheet(String deviceId) {
        try {
            // Get spreadsheet metadata to find all sheets
            String url = SHEETS_API_BASE + sheetsId + "?key=" + apiKey;
            Log.d(TAG, "Getting spreadsheet metadata from: " + url);
            
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API HTTP error: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            
            // Parse the response to find sheets
            org.json.JSONObject spreadsheet = new org.json.JSONObject(response.toString());
            org.json.JSONArray sheets = spreadsheet.getJSONArray("sheets");
            
            // Look for a sheet that exactly matches the device ID
            for (int i = 0; i < sheets.length(); i++) {
                org.json.JSONObject sheet = sheets.getJSONObject(i);
                org.json.JSONObject properties = sheet.getJSONObject("properties");
                String sheetTitle = properties.getString("title");
                
                Log.d(TAG, "Found sheet: " + sheetTitle);
                
                // Check if sheet title matches the device ID exactly
                if (sheetTitle.equalsIgnoreCase(deviceId)) {
                    Log.i(TAG, "Found matching sheet: " + sheetTitle + " for device: " + deviceId);
                    return sheetTitle;
                }
            }
            
            Log.w(TAG, "No sheet found matching device ID: " + deviceId);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding device sheet", e);
            return null;
        }
    }
    
    private DeviceConfig loadConfigFromSheet(String deviceId, String sheetName) {
        try {
            // Get values from the specific sheet
            String range = sheetName + "!A1:Z100"; // Adjust range as needed
            String url = SHEETS_API_BASE + sheetsId + "/values/" + range + "?key=" + apiKey;
            Log.d(TAG, "Loading device config from: " + url);
            
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API HTTP error: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            
            return parseDeviceConfigFromAPI(deviceId, response.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading from sheet", e);
            return null;
        }
    }
    
    private DeviceConfig parseDeviceConfigFromCsv(String deviceId, List<String> csvLines) {
        try {
            if (csvLines.size() < 5) {
                Log.e(TAG, "Sheet too short, expected at least 5 rows");
                return null;
            }
            
            // Parse configuration from your sheet structure:
            // Row 1: "Refresh Minutes", refresh interval value (in column B)
            // Row 4: "URL", "DisplaySeconds" (headers)
            // Row 5+: actual URL, display time values
            
            String[] row1 = parseCsvLine(csvLines.get(0));
            
            // Extract refresh interval from row 1, column B (index 1)
            int refreshInterval = 60; // default
            if (row1.length > 1) {
                try {
                    refreshInterval = Integer.parseInt(row1[1].trim());
                    Log.d(TAG, "Parsed refresh interval: " + refreshInterval + " minutes");
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid refresh interval, using default: " + row1[1]);
                }
            }
            
            // Parse pages starting from row 5 (index 4) - after the URL/DisplaySeconds headers in row 4
            List<PageConfig> pages = new ArrayList<>();
            for (int i = 4; i < csvLines.size(); i++) {
                String[] pageRow = parseCsvLine(csvLines.get(i));
                if (pageRow.length >= 2 && !pageRow[0].trim().isEmpty()) {
                    String pageUrl = pageRow[0].trim();
                    
                    // Skip if this is a header row
                    if (pageUrl.equalsIgnoreCase("URL")) {
                        Log.d(TAG, "Skipping header row: " + pageUrl);
                        continue;
                    }
                    
                    int displayTime = 30; // default
                    if (pageRow.length > 1) {
                        try {
                            displayTime = Integer.parseInt(pageRow[1].trim());
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid display time, using default: " + pageRow[1]);
                        }
                    }
                    
                    PageConfig pageConfig = new PageConfig(pageUrl, displayTime);
                    // Title field removed - not needed for kiosk display
                    pages.add(pageConfig);
                    
                    Log.d(TAG, "Added page: " + pageUrl + " (display: " + displayTime + "s)");
                }
            }
            
            if (pages.isEmpty()) {
                Log.e(TAG, "No pages found in configuration");
                return null;
            }
            
            String deviceName = deviceId + " Device";
            Log.i(TAG, "Loaded config for " + deviceName + " with " + pages.size() + " pages, refresh: " + refreshInterval + "min");
            
            DeviceConfig config = new DeviceConfig();
            config.setDeviceId(deviceId);
            config.setDeviceName(deviceName);
            config.setOrientation("landscape"); // Default orientation, will be overridden by stored preference
            config.setRefreshIntervalMinutes(refreshInterval);
            // autoStart and clearCache fields removed - not used in simplified kiosk
            config.setPages(pages);
            
            return config;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sheet config", e);
            return null;
        }
    }
    
    private DeviceConfig parseDeviceConfigFromAPI(String deviceId, String jsonResponse) {
        try {
            // Parse the Google Sheets API JSON response
            org.json.JSONObject response = new org.json.JSONObject(jsonResponse);
            org.json.JSONArray values = response.getJSONArray("values");
            
            // Convert JSON array to list of strings for compatibility with existing parser
            List<String> csvLines = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                org.json.JSONArray row = values.getJSONArray(i);
                StringBuilder csvLine = new StringBuilder();
                for (int j = 0; j < row.length(); j++) {
                    if (j > 0) csvLine.append(",");
                    String cell = row.optString(j, "");
                    // Escape commas and quotes in CSV format
                    if (cell.contains(",") || cell.contains("\"")) {
                        cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                    }
                    csvLine.append(cell);
                }
                csvLines.add(csvLine.toString());
            }
            
            return parseDeviceConfigFromCsv(deviceId, csvLines);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing API response", e);
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
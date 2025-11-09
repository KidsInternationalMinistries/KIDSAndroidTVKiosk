package com.kidsim.tvkiosk.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

/**
 * Utility class for common JSON parsing operations to reduce code duplication
 */
public class JsonUtils {
    private static final String TAG = "JsonUtils";
    
    /**
     * Safely parse JSON string into JSONObject with error handling
     */
    public static JSONObject parseJsonString(String jsonString, String context) {
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to parse " + context + " JSON", e);
            return null;
        }
    }
    
    /**
     * Safely get string value from JSONObject with default fallback
     */
    public static String getStringOrDefault(JSONObject json, String key, String defaultValue) {
        try {
            return json.has(key) ? json.getString(key) : defaultValue;
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to get string '" + key + "'", e);
            return defaultValue;
        }
    }
    
    /**
     * Safely get int value from JSONObject with default fallback
     */
    public static int getIntOrDefault(JSONObject json, String key, int defaultValue) {
        try {
            return json.has(key) ? json.getInt(key) : defaultValue;
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to get int '" + key + "'", e);
            return defaultValue;
        }
    }
    
    /**
     * Safely get boolean value from JSONObject with default fallback
     */
    public static boolean getBooleanOrDefault(JSONObject json, String key, boolean defaultValue) {
        try {
            return json.has(key) ? json.getBoolean(key) : defaultValue;
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to get boolean '" + key + "'", e);
            return defaultValue;
        }
    }
    
    /**
     * Safely get JSONArray from JSONObject
     */
    public static JSONArray getJsonArrayOrNull(JSONObject json, String key) {
        try {
            return json.has(key) ? json.getJSONArray(key) : null;
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to get JSONArray '" + key + "'", e);
            return null;
        }
    }
    
    /**
     * Safely get JSONObject from JSONObject
     */
    public static JSONObject getJsonObjectOrNull(JSONObject json, String key) {
        try {
            return json.has(key) ? json.getJSONObject(key) : null;
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to get JSONObject '" + key + "'", e);
            return null;
        }
    }
    
    /**
     * Find asset by name pattern in GitHub release assets array
     */
    public static String findAssetDownloadUrl(JSONArray assets, String namePattern) {
        try {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = getStringOrDefault(asset, "name", "");
                if (assetName.contains(namePattern)) {
                    return getStringOrDefault(asset, "browser_download_url", null);
                }
            }
        } catch (JSONException e) {
            ErrorHandler.logError(TAG, "Failed to parse assets array", e);
        }
        return null;
    }
    
    /**
     * Extract sheet properties from Google Sheets API response
     */
    public static String extractSheetTitle(JSONObject sheet) {
        JSONObject properties = getJsonObjectOrNull(sheet, "properties");
        return properties != null ? getStringOrDefault(properties, "title", null) : null;
    }
}
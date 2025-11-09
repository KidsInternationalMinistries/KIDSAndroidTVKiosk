package com.kidsim.tvkiosk.utils;

import android.util.Log;

/**
 * Centralized error handling utility to reduce duplicate logging patterns
 * and provide consistent error handling across the application.
 */
public class ErrorHandler {
    
    /**
     * Log an error with exception and return the formatted error message
     */
    public static String logError(String tag, String message, Exception e) {
        Log.e(tag, message, e);
        return message + ": " + e.getMessage();
    }
    
    /**
     * Log an error message without exception
     */
    public static void logError(String tag, String message) {
        Log.e(tag, message);
    }
    
    /**
     * Log error with exception and execute callback with formatted message
     */
    public static void logErrorWithCallback(String tag, String message, Exception e, ErrorCallback callback) {
        String errorMessage = logError(tag, message, e);
        if (callback != null) {
            callback.onError(errorMessage);
        }
    }
    
    /**
     * Log error and execute callback
     */
    public static void logErrorWithCallback(String tag, String message, ErrorCallback callback) {
        logError(tag, message);
        if (callback != null) {
            callback.onError(message);
        }
    }
    
    /**
     * Log network/API error with standardized HTTP error format
     */
    public static String logApiError(String tag, String operation, int responseCode) {
        String message = operation + " failed with HTTP code: " + responseCode;
        Log.e(tag, message);
        return message;
    }
    
    /**
     * Log API error with exception
     */
    public static String logApiError(String tag, String operation, Exception e) {
        String message = "Error during " + operation;
        Log.e(tag, message, e);
        return message + ": " + e.getMessage();
    }
    
    /**
     * Log configuration error with specific formatting
     */
    public static String logConfigError(String tag, String configType, String error) {
        String message = configType + " configuration error: " + error;
        Log.e(tag, message);
        return message;
    }
    
    /**
     * Callback interface for error handling
     */
    public interface ErrorCallback {
        void onError(String errorMessage);
    }
    
    /**
     * Structured logging utilities for consistent info messages
     */
    public static void logActivityStart(String tag, String activityName) {
        Log.i(tag, activityName + " starting");
    }
    
    public static void logActivityEnd(String tag, String activityName) {
        Log.i(tag, activityName + " destroying");
    }
    
    public static void logConfigInfo(String tag, String configType, String deviceName) {
        Log.i(tag, configType + " configuration loaded for device: " + deviceName);
    }
    
    public static void logUserAction(String tag, String action) {
        Log.i(tag, action + " clicked");
    }
    
    public static void logNetworkStatus(String tag, boolean connected) {
        Log.i(tag, "Network connectivity changed: " + (connected ? "CONNECTED" : "DISCONNECTED"));
    }
    
    public static void logServiceStatus(String tag, String serviceName, boolean started) {
        Log.i(tag, serviceName + " " + (started ? "started" : "stopped"));
    }
    
    public static void logApiCall(String tag, String operation, String url) {
        Log.i(tag, "Starting " + operation + " API call: " + url);
    }
    
    public static void logApiResponse(String tag, String operation, int responseCode) {
        Log.i(tag, operation + " API response code: " + responseCode);
    }
}
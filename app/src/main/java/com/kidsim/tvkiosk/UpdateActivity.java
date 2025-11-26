package com.kidsim.tvkiosk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.content.Intent;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import com.kidsim.tvkiosk.config.DeviceIdManager;
import com.kidsim.tvkiosk.config.GoogleSheetsConfigLoader;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends Activity {
    
    private static final String TAG = "UpdateActivity";
    private static final String GITHUB_LATEST_RELEASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest";
    private static final String FALLBACK_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest/download/app-release.apk";
    
    // UI components
    private TextView statusText;
    private TextView currentVersionText;
    private Spinner orientationSpinner;
    private Spinner deviceIdSpinner;
    private Button kioskButton;
    private Button exitButton;
    
    // Configuration and preferences
    private SharedPreferences preferences;
    private boolean isFirstTimeSetup;
    private DeviceIdManager deviceIdManager;
    private ConfigurationManager configManager;
    
    // Background task executor
    private ExecutorService executor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        
        Log.i(TAG, "UpdateActivity started");
        
        // Check if this is first-time setup
        isFirstTimeSetup = getIntent().getBooleanExtra("firstTimeSetup", false);
        
        // Initialize components
        preferences = getSharedPreferences("KioskUpdatePrefs", MODE_PRIVATE);
        deviceIdManager = new DeviceIdManager(this);
        configManager = new ConfigurationManager(this);
        executor = Executors.newSingleThreadExecutor();
        
        initializeViews();
        setupSpinners();
        setupEventHandlers();
        loadDeviceIds();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        currentVersionText = findViewById(R.id.currentVersionText);
        orientationSpinner = findViewById(R.id.orientationSpinner);
        deviceIdSpinner = findViewById(R.id.deviceIdSpinner);
        kioskButton = findViewById(R.id.kioskButton);
        exitButton = findViewById(R.id.exitButton);
        
        // Display current version
        displayCurrentVersion();
    }
    
    private void displayCurrentVersion() {
        try {
            String packageName = getPackageName();
            String versionName = getPackageManager().getPackageInfo(packageName, 0).versionName;
            currentVersionText.setText("Current Version: " + versionName);
            Log.i(TAG, "Current app version: " + versionName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get current version", e);
            currentVersionText.setText("Current Version: Unknown");
        }
    }
    
    private void setupSpinners() {
        // Setup orientation spinner with display-friendly labels
        List<String> orientationOptions = new ArrayList<>();
        orientationOptions.add("0° (Normal)");
        orientationOptions.add("90° (Rotate Right)");
        orientationOptions.add("180° (Upside Down)");
        orientationOptions.add("270° (Rotate Left)");
        
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, orientationOptions);
        orientationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orientationSpinner.setAdapter(orientationAdapter);
        
        // Set current orientation selection
        String currentOrientation = deviceIdManager.getOrientation();
        String displayOrientation = convertStoredToDisplayOrientation(currentOrientation);
        int orientationIndex = orientationOptions.indexOf(displayOrientation);
        if (orientationIndex >= 0) {
            orientationSpinner.setSelection(orientationIndex);
            Log.d(TAG, "Set orientation spinner to: " + displayOrientation + " (index: " + orientationIndex + ")");
        }
        
        // Setup device ID spinner with validation
        orientationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                checkAllSelectionsValid();
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                checkAllSelectionsValid();
            }
        });
        
        deviceIdSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                checkAllSelectionsValid();
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                checkAllSelectionsValid();
            }
        });
    }
    
    private String convertStoredToDisplayOrientation(String storedOrientation) {
        // Convert stored degrees to display format
        if ("0".equals(storedOrientation)) {
            return "0° (Normal)";
        } else if ("90".equals(storedOrientation)) {
            return "90° (Rotate Right)";
        } else if ("180".equals(storedOrientation)) {
            return "180° (Upside Down)";
        } else if ("270".equals(storedOrientation)) {
            return "270° (Rotate Left)";
        } else {
            return "0° (Normal)"; // fallback
        }
    }
    
    private void checkAllSelectionsValid() {
        // Build type is automatic, just check orientation and device ID
        boolean hasValidSelections = 
            orientationSpinner.getSelectedItem() != null &&
            deviceIdSpinner.getSelectedItem() != null &&
            !deviceIdSpinner.getSelectedItem().toString().isEmpty();
        
        String appType = "Release"; // Unified app
        Log.d(TAG, "Selections valid: " + hasValidSelections + 
               " (App Type: " + appType + " (unified)" +
               ", Orientation: " + (orientationSpinner.getSelectedItem() != null ? orientationSpinner.getSelectedItem().toString() : "null") +
               ", DeviceId: " + (deviceIdSpinner.getSelectedItem() != null ? deviceIdSpinner.getSelectedItem().toString() : "null") + ")");
    }
    
    private void setupEventHandlers() {
        kioskButton.setOnClickListener(v -> returnToKiosk());
        exitButton.setOnClickListener(v -> exitApp());
    }
    
    private void loadDeviceIds() {
        statusText.setText("Loading device IDs from Google Sheets...");
        
        // Initialize Google Sheets loader with API credentials
        String apiKey = configManager.getGoogleSheetsApiKey();
        String sheetsId = configManager.getGoogleSheetsId();
        
        if (apiKey == null || sheetsId == null) {
            statusText.setText("Error: Google Sheets credentials not configured");
            return;
        }
        
        GoogleSheetsConfigLoader sheetsLoader = new GoogleSheetsConfigLoader(sheetsId, apiKey);
        
        executor.execute(() -> {
            try {
                // Load device IDs from Google Sheets using async API
                sheetsLoader.loadAvailableDeviceIds(new GoogleSheetsConfigLoader.DeviceListListener() {
                    @Override
                    public void onDeviceListLoaded(List<String> deviceIds) {
                        runOnUiThread(() -> {
                            if (deviceIds != null && !deviceIds.isEmpty()) {
                                Log.i(TAG, "Loaded " + deviceIds.size() + " device IDs from Google Sheets");
                                
                                // Create adapter for device ID spinner
                                ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                                    UpdateActivity.this, android.R.layout.simple_spinner_item, deviceIds);
                                deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                deviceIdSpinner.setAdapter(deviceAdapter);
                                
                                // Set current device ID selection if available
                                String currentDeviceId = deviceIdManager.getDeviceId();
                                if (currentDeviceId != null && deviceIds.contains(currentDeviceId)) {
                                    int deviceIndex = deviceIds.indexOf(currentDeviceId);
                                    deviceIdSpinner.setSelection(deviceIndex);
                                    Log.d(TAG, "Set device ID spinner to: " + currentDeviceId + " (index: " + deviceIndex + ")");
                                }
                                
                                statusText.setText("Device IDs loaded successfully. Select your configuration and click 'Launch Kiosk'.");
                                checkAllSelectionsValid();
                            } else {
                                Log.w(TAG, "No device IDs found or failed to load from Google Sheets");
                                statusText.setText("Warning: Failed to load device IDs from Google Sheets. Using default list.");
                                
                                // Use fallback list
                                List<String> fallbackIds = new ArrayList<>();
                                fallbackIds.add("device001");
                                fallbackIds.add("device002");
                                fallbackIds.add("device003");
                                
                                ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                                    UpdateActivity.this, android.R.layout.simple_spinner_item, fallbackIds);
                                deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                deviceIdSpinner.setAdapter(deviceAdapter);
                                
                                checkAllSelectionsValid();
                            }
                        });
                    }

                    @Override
                    public void onDeviceListFailed(String error) {
                        Log.e(TAG, "Error loading device IDs: " + error);
                        runOnUiThread(() -> {
                            statusText.setText("Error loading device IDs: " + error);
                            
                            // Use fallback list on error
                            List<String> fallbackIds = new ArrayList<>();
                            fallbackIds.add("device001");
                            fallbackIds.add("device002");
                            fallbackIds.add("device003");
                            
                            ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                                UpdateActivity.this, android.R.layout.simple_spinner_item, fallbackIds);
                            deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            deviceIdSpinner.setAdapter(deviceAdapter);
                            
                            checkAllSelectionsValid();
                        });
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading device IDs", e);
                runOnUiThread(() -> {
                    statusText.setText("Error loading device IDs: " + e.getMessage());
                    
                    // Use minimal fallback list  
                    List<String> fallbackIds = new ArrayList<>();
                    fallbackIds.add("device001");
                    fallbackIds.add("device002");
                    fallbackIds.add("device003");
                    
                    ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                        UpdateActivity.this, android.R.layout.simple_spinner_item, fallbackIds);
                    deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    deviceIdSpinner.setAdapter(deviceAdapter);
                    
                    checkAllSelectionsValid();
                });
            }
        });
    }
    
    /**
     * Check if this is the debug app (no longer used - simplified workflow)
     */
    private boolean isDebugApp() {
        // Always return false - unified app, no separate debug package
        return false;
    }
    
    /**
     * Return to kiosk by closing this activity and going back to MainActivity
     */
    private void returnToKiosk() {
        Log.i(TAG, "Returning to kiosk mode");
        
        // Save any pending preferences first
        saveCurrentSettings();
        
        try {
            // Create intent to start MainActivity
            Intent kioskIntent = new Intent(this, MainActivity.class);
            
            // Add flag to indicate we're returning from configuration
            // This will tell MainActivity to skip device ID configuration check
            kioskIntent.putExtra("returnFromConfiguration", true);
            
            // Clear the task and start MainActivity as a new task
            kioskIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            // Start MainActivity
            startActivity(kioskIntent);
            
            // Finish this activity
            finish();
            
            Log.i(TAG, "Successfully started MainActivity and finished UpdateActivity");
            
        } catch (Exception e) {
            Log.e(TAG, "Error returning to kiosk", e);
            // If there's an error, just finish this activity
            finish();
        }
    }
    
    /**
     * Exit the entire application
     */
    private void exitApp() {
        Log.i(TAG, "Exiting application completely");
        
        // Save any pending settings first
        saveCurrentSettings();
        
        // Close executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        
        // Exit the entire app
        finishAndRemoveTask();
        System.exit(0);
    }
    
    /**
     * Save current spinner selections to preferences and DeviceIdManager
     */
    private void saveCurrentSettings() {
        try {
            // Get selected values
            String displayOrientation = orientationSpinner.getSelectedItem() != null ? 
                orientationSpinner.getSelectedItem().toString() : "0° (Normal)";
            String orientation = convertDisplayToStoredOrientation(displayOrientation);
            String deviceId = deviceIdSpinner.getSelectedItem() != null ? 
                deviceIdSpinner.getSelectedItem().toString() : "";
            
            // Save to preferences
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("buildType", "Release"); // Unified app
            editor.putString("deviceId", deviceId);
            editor.apply();
            
            // Save orientation using DeviceIdManager
            deviceIdManager.setOrientation(orientation);
            
            // Save device ID using DeviceIdManager if not blank
            if (!deviceId.isEmpty()) {
                deviceIdManager.setDeviceId(deviceId);
            }
            
            Log.i(TAG, "Saved settings - Orientation: " + orientation + ", Device: " + deviceId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving settings", e);
        }
    }
    
    private String convertDisplayToStoredOrientation(String displayOrientation) {
        // Convert display format back to stored degrees
        if ("0° (Normal)".equals(displayOrientation)) {
            return "0";
        } else if ("90° (Rotate Right)".equals(displayOrientation)) {
            return "90";
        } else if ("180° (Upside Down)".equals(displayOrientation)) {
            return "180";
        } else if ("270° (Rotate Left)".equals(displayOrientation)) {
            return "270";
        } else {
            return "0"; // fallback
        }
    }
}
package com.kidsim.tvkiosk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import com.kidsim.tvkiosk.config.DeviceIdManager;
import com.kidsim.tvkiosk.config.GoogleSheetsConfigLoader;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends Activity {
    private static final String TAG = "UpdateActivity";
    
    private SharedPreferences preferences;
    private DeviceIdManager deviceIdManager;
    private ConfigurationManager configManager;
    private GoogleSheetsConfigLoader googleSheetsLoader;
    private ExecutorService executor;
    
    // UI Components
    private Spinner buildTypeSpinner;
    private Spinner orientationSpinner;
    private Spinner deviceIdSpinner;
    private TextView statusText;
    private TextView currentVersionText;
    private Button kioskButton;
    private Button exitButton;
    
    private List<String> availableDeviceIds;
    private boolean isFirstTimeSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        
        Log.i(TAG, "UpdateActivity started");
        
        // Check if this is first-time setup
        isFirstTimeSetup = getIntent().getBooleanExtra("firstTimeSetup", false);
        
        // Initialize components
        preferences = getSharedPreferences("DeviceConfig", MODE_PRIVATE);  // Use same prefs as DeviceIdManager
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
        buildTypeSpinner = findViewById(R.id.buildTypeSpinner);
        orientationSpinner = findViewById(R.id.orientationSpinner);
        deviceIdSpinner = findViewById(R.id.deviceIdSpinner);
        statusText = findViewById(R.id.statusText);
        currentVersionText = findViewById(R.id.currentVersionText);
        kioskButton = findViewById(R.id.kioskButton);
        exitButton = findViewById(R.id.exitButton);
        
        // Display current version
        displayCurrentVersion();
    }

    private void displayCurrentVersion() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionText.setText("Current Version: " + versionName + " (build " + versionCode + ")");
        } catch (Exception e) {
            currentVersionText.setText("Current Version: Unknown");
            Log.e(TAG, "Failed to get version info", e);
        }
    }

    private void setupSpinners() {
        // Build Type Spinner
        List<String> buildTypes = new ArrayList<>();
        buildTypes.add("Release");
        buildTypes.add("Debug");
        ArrayAdapter<String> buildAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, buildTypes);
        buildAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        buildTypeSpinner.setAdapter(buildAdapter);
        
        // Orientation Spinner
        List<String> orientations = new ArrayList<>();
        orientations.add("Landscape");
        orientations.add("Portrait");
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, orientations);
        orientationAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        orientationSpinner.setAdapter(orientationAdapter);
        
        // Load saved preferences
        loadSavedPreferences();
        
        // Add listeners to all spinners to auto-save changes
        setupSpinnerListeners();
    }

    private void loadSavedPreferences() {
        // Load saved build type (default: Release)
        String savedBuildType = preferences.getString("buildType", "Release");
        for (int i = 0; i < buildTypeSpinner.getCount(); i++) {
            if (buildTypeSpinner.getItemAtPosition(i).toString().equals(savedBuildType)) {
                buildTypeSpinner.setSelection(i);
                break;
            }
        }
        
        // Load saved orientation (default: Landscape)
        String savedOrientation = preferences.getString("orientation", "Landscape");
        for (int i = 0; i < orientationSpinner.getCount(); i++) {
            if (orientationSpinner.getItemAtPosition(i).toString().equals(savedOrientation)) {
                orientationSpinner.setSelection(i);
                break;
            }
        }
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
            Log.w(TAG, "Missing Google Sheets credentials, using basic setup");
            setupDeviceIdSpinner();
            return;
        }
        
        // Load device IDs from Google Sheets using the correct method
        try {
            googleSheetsLoader = new GoogleSheetsConfigLoader(sheetsId, apiKey);
            googleSheetsLoader.loadAvailableDeviceIds(new GoogleSheetsConfigLoader.DeviceListListener() {
                @Override
                public void onDeviceListLoaded(List<String> deviceIds) {
                    availableDeviceIds = deviceIds;
                    runOnUiThread(() -> {
                        setupDeviceIdSpinner();
                        statusText.setText("Ready");
                    });
                }
                
                @Override
                public void onDeviceListFailed(String error) {
                    Log.e(TAG, "Failed to load device IDs: " + error);
                    runOnUiThread(() -> {
                        setupDeviceIdSpinner();
                        statusText.setText("Failed to load device IDs from cloud, using basic setup");
                    });
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Google Sheets loader", e);
            setupDeviceIdSpinner();
            statusText.setText("Failed to initialize cloud connection, using basic setup");
        }
    }

    private void setupDeviceIdSpinner() {
        List<String> deviceIds = new ArrayList<>();
        
        if (availableDeviceIds != null && !availableDeviceIds.isEmpty()) {
            deviceIds.addAll(availableDeviceIds);
        } else {
            // Fallback device IDs
            deviceIds.add("device-1");
            deviceIds.add("device-2");
            deviceIds.add("device-3");
            deviceIds.add("test-device");
        }
        
        ArrayAdapter<String> deviceIdAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, deviceIds);
        deviceIdAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        deviceIdSpinner.setAdapter(deviceIdAdapter);
        
        // Load saved device ID
        String savedDeviceId = preferences.getString("deviceId", "");
        if (!savedDeviceId.isEmpty()) {
            for (int i = 0; i < deviceIdSpinner.getCount(); i++) {
                if (deviceIdSpinner.getItemAtPosition(i).toString().equals(savedDeviceId)) {
                    deviceIdSpinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setupSpinnerListeners() {
        AdapterView.OnItemSelectedListener selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Save all configuration changes immediately
                if (parent == buildTypeSpinner && buildTypeSpinner.getSelectedItem() != null) {
                    String buildType = buildTypeSpinner.getSelectedItem().toString();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("buildType", buildType);
                    editor.apply();
                    Log.i(TAG, "Auto-saved build type: " + buildType);
                }
                
                if (parent == orientationSpinner && orientationSpinner.getSelectedItem() != null) {
                    String orientation = orientationSpinner.getSelectedItem().toString();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("orientation", orientation);
                    editor.apply();
                    
                    // Apply orientation change immediately to UpdateActivity
                    applyOrientationToActivity(orientation);
                    Log.i(TAG, "Auto-saved and applied orientation: " + orientation + " to both preference stores");
                }
                
                if (parent == deviceIdSpinner && deviceIdSpinner.getSelectedItem() != null) {
                    String deviceId = deviceIdSpinner.getSelectedItem().toString();
                    if (!deviceId.isEmpty() && !deviceId.startsWith("Loading")) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("deviceId", deviceId);
                        editor.apply();
                        
                        // Also save device ID using DeviceIdManager
                        deviceIdManager.setDeviceId(deviceId);
                        Log.i(TAG, "Auto-saved device ID: " + deviceId);
                    }
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        };
        
        buildTypeSpinner.setOnItemSelectedListener(selectionListener);
        orientationSpinner.setOnItemSelectedListener(selectionListener);
        deviceIdSpinner.setOnItemSelectedListener(selectionListener);
    }

    /**
     * Apply orientation change immediately to the current UpdateActivity
     * Forces the display to rotate 90 degrees for Portrait, regardless of device orientation
     */
    private void applyOrientationToActivity(String orientation) {
        try {
            int orientationValue = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            if ("Portrait".equalsIgnoreCase(orientation)) {
                orientationValue = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }
            setRequestedOrientation(orientationValue);
            Log.d(TAG, "Applied forced orientation " + orientation + " to UpdateActivity (rotated display by 90 degrees)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply orientation: " + e.getMessage());
        }
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
            kioskIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(kioskIntent);
            
            // Close this activity
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to return to kiosk", e);
            // Fallback: just close this activity
            finish();
        }
    }

    /**
     * Save current settings from spinners
     */
    private void saveCurrentSettings() {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            
            if (buildTypeSpinner.getSelectedItem() != null) {
                editor.putString("buildType", buildTypeSpinner.getSelectedItem().toString());
            }
            
            if (orientationSpinner.getSelectedItem() != null) {
                editor.putString("orientation", orientationSpinner.getSelectedItem().toString());
            }
            
            if (deviceIdSpinner.getSelectedItem() != null) {
                String deviceId = deviceIdSpinner.getSelectedItem().toString();
                editor.putString("deviceId", deviceId);
                deviceIdManager.setDeviceId(deviceId);
            }
            
            editor.apply();
            Log.i(TAG, "Saved current settings");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save current settings", e);
        }
    }

    /**
     * Exit the app completely
     */
    private void exitApp() {
        Log.i(TAG, "Exiting app");
        
        // Save current settings first
        saveCurrentSettings();
        
        // Close all activities and exit
        finishAffinity();
        System.exit(0);
    }
}
package com.kidsim.tvkiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.kidsim.tvkiosk.config.DeviceIdManager;
import com.kidsim.tvkiosk.config.GoogleSheetsConfigLoader;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateActivity extends Activity {
    private static final String TAG = "UpdateActivity";
    
    // GitHub API URLs to get latest release information
    private static final String GITHUB_LATEST_RELEASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest";
    private static final String GITHUB_PRERELEASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tags/prerelease";
    
    // Fallback URL in case API fails
    private static final String FALLBACK_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest/download/kids-kiosk.apk";
    
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
    private Button saveAndInstallButton;
    private Button preReleaseButton;
    private Button kioskButton;
    private Button exitButton;
    
    private List<String> availableDeviceIds;
    private boolean isFirstTimeSetup = false;
    private long downloadId = -1;
    
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
        
        // Note: Removed BroadcastReceiver - using periodic status checking instead
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        // Note: No BroadcastReceiver to unregister anymore
    }
    
    private void initializeViews() {
        buildTypeSpinner = findViewById(R.id.buildTypeSpinner);
        orientationSpinner = findViewById(R.id.orientationSpinner);
        deviceIdSpinner = findViewById(R.id.deviceIdSpinner);
        statusText = findViewById(R.id.statusText);
        currentVersionText = findViewById(R.id.currentVersionText);
        saveAndInstallButton = findViewById(R.id.saveAndInstallButton);
        preReleaseButton = findViewById(R.id.preReleaseButton);
        kioskButton = findViewById(R.id.kioskButton);
        exitButton = findViewById(R.id.exitButton);
        
        // Start with update buttons disabled until all selections are made
        saveAndInstallButton.setEnabled(false);
        preReleaseButton.setEnabled(false);
        
        // Set button text based on app type
        updateButtonText();
        
        // Display current version
        displayCurrentVersion();
    }
    
    private void updateButtonText() {
        if (isDebugApp()) {
            saveAndInstallButton.setText("Install Update");
        } else {
            saveAndInstallButton.setText("Install Update");
        }
    }
    
    private void displayCurrentVersion() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionText.setText("Current Version: " + versionName + " (build " + versionCode + ")");
        } catch (Exception e) {
            Log.e(TAG, "Could not get version info", e);
            currentVersionText.setText("Current Version: Unknown");
        }
    }
    
    private void setupSpinners() {
        // Simple approach: Show what type of app this is, no dropdown selection
        List<String> buildTypes = new ArrayList<>();
        List<String> orientations = new ArrayList<>();
        
        // Each app knows what it is - no selection needed
        if (isDebugApp()) {
            buildTypes.add("Debug");
            statusText.setText("Debug app - will update to latest debug version");
        } else {
            buildTypes.add("Release");
            statusText.setText("Release app - will update to latest release version");
        }
        
        // Keep orientation selection
        orientations.add("Landscape");
        orientations.add("Portrait");
        
        // Set up adapters (build type is now read-only)
        ArrayAdapter<String> buildAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, buildTypes);
        buildAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        buildTypeSpinner.setAdapter(buildAdapter);
        buildTypeSpinner.setEnabled(false); // Make it read-only
        
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, orientations);
        orientationAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        orientationSpinner.setAdapter(orientationAdapter);
        
        // Load saved preferences
        loadSavedPreferences();
    }
    
    private void loadSavedPreferences() {
        // Build type is now automatic based on app type, so only load orientation
        
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
        saveAndInstallButton.setOnClickListener(v -> saveAndInstall());
        preReleaseButton.setOnClickListener(v -> saveAndInstallPreRelease());
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
        
        googleSheetsLoader = new GoogleSheetsConfigLoader(sheetsId, apiKey);
        
        googleSheetsLoader.loadAvailableDeviceIds(new GoogleSheetsConfigLoader.DeviceListListener() {
            @Override
            public void onDeviceListLoaded(List<String> deviceIds) {
                runOnUiThread(() -> {
                    availableDeviceIds = deviceIds;
                    setupDeviceIdSpinner();
                    statusText.setText("Ready to configure");
                });
            }
            
            @Override
            public void onDeviceListFailed(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load device IDs: " + error);
                    statusText.setText("Failed to load device IDs: " + error);
                    
                    // Use fallback device IDs
                    availableDeviceIds = new ArrayList<>();
                    availableDeviceIds.add("Test1");
                    availableDeviceIds.add("Test2");
                    setupDeviceIdSpinner();
                });
            }
        });
    }
    
    private void setupDeviceIdSpinner() {
        if (availableDeviceIds == null || availableDeviceIds.isEmpty()) {
            availableDeviceIds = new ArrayList<>();
            availableDeviceIds.add(""); // Blank default
        }
        
        // Add blank option if not present
        if (!availableDeviceIds.contains("")) {
            availableDeviceIds.add(0, ""); // Add blank at beginning
        }
        
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, 
            R.layout.spinner_item, availableDeviceIds);
        deviceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        deviceIdSpinner.setAdapter(deviceAdapter);
        
        // Load saved device ID (default: blank)
        String savedDeviceId = preferences.getString("deviceId", "");
        for (int i = 0; i < deviceIdSpinner.getCount(); i++) {
            if (deviceIdSpinner.getItemAtPosition(i).toString().equals(savedDeviceId)) {
                deviceIdSpinner.setSelection(i);
                break;
            }
        }
        
        // Add listeners to all spinners to check when button should be enabled
        setupSpinnerListeners();
    }
    
    private void setupSpinnerListeners() {
        AdapterView.OnItemSelectedListener selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                checkAllSelectionsValid();
                
                // Save orientation and device ID changes immediately
                if (parent == orientationSpinner && orientationSpinner.getSelectedItem() != null) {
                    String orientation = orientationSpinner.getSelectedItem().toString();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("orientation", orientation);
                    editor.apply();
                    
                    // Also save orientation using DeviceIdManager
                    deviceIdManager.setOrientation(orientation);
                    Log.i(TAG, "Auto-saved orientation: " + orientation);
                }
                
                if (parent == deviceIdSpinner && deviceIdSpinner.getSelectedItem() != null) {
                    String deviceId = deviceIdSpinner.getSelectedItem().toString();
                    if (!deviceId.isEmpty()) {
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
                checkAllSelectionsValid();
            }
        };
        
        buildTypeSpinner.setOnItemSelectedListener(selectionListener);
        orientationSpinner.setOnItemSelectedListener(selectionListener);
        deviceIdSpinner.setOnItemSelectedListener(selectionListener);
        
        // Initial check
        checkAllSelectionsValid();
    }
    
    private void checkAllSelectionsValid() {
        // Build type is automatic, just check orientation and device ID
        boolean hasValidSelections = 
            orientationSpinner.getSelectedItem() != null &&
            deviceIdSpinner.getSelectedItem() != null &&
            !deviceIdSpinner.getSelectedItem().toString().isEmpty();
        
        saveAndInstallButton.setEnabled(hasValidSelections);
        preReleaseButton.setEnabled(hasValidSelections);
        
        // Make sure the button visual state updates properly
        saveAndInstallButton.refreshDrawableState();
        preReleaseButton.refreshDrawableState();
        
        String appType = "Release"; // Unified app
        Log.d(TAG, "Button enabled: " + hasValidSelections + 
               " (App Type: " + appType + " (unified)" +
               ", Orientation: " + (orientationSpinner.getSelectedItem() != null ? orientationSpinner.getSelectedItem().toString() : "null") +
               ", DeviceId: " + (deviceIdSpinner.getSelectedItem() != null ? deviceIdSpinner.getSelectedItem().toString() : "null") + ")");
    }
    
    private void saveAndInstallPreRelease() {
        Log.i(TAG, "saveAndInstallPreRelease() called - PreRelease button clicked");
        
        // Get selected values
        String orientation = orientationSpinner.getSelectedItem().toString();
        String deviceId = deviceIdSpinner.getSelectedItem().toString();
        
        // App is unified - no separate debug/release builds
        String buildType = "PreRelease";
        
        Log.i(TAG, "Selected values - BuildType: " + buildType + ", Orientation: " + orientation + ", DeviceId: " + deviceId);
        
        // Save preferences
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("buildType", buildType);
        editor.putString("orientation", orientation);
        editor.putString("deviceId", deviceId);
        editor.apply();
        
        // Save device ID using DeviceIdManager if not blank
        if (!deviceId.isEmpty()) {
            deviceIdManager.setDeviceId(deviceId);
        }
        
        Log.i(TAG, "Saved settings - Build: " + buildType + ", Orientation: " + orientation + ", Device: " + deviceId);
        
        // Start download and installation for prerelease
        downloadAndInstallAPK(buildType);
    }
    
    private void saveAndInstall() {
        Log.i(TAG, "saveAndInstall() called - Save and Install button clicked");
        
        // Get selected values
        String orientation = orientationSpinner.getSelectedItem().toString();
        String deviceId = deviceIdSpinner.getSelectedItem().toString();
        
        // App is now unified - no separate debug/release builds
        String buildType = "Release";
        
        Log.i(TAG, "Selected values - BuildType: " + buildType + " (unified), Orientation: " + orientation + ", DeviceId: " + deviceId);
        
        // Save preferences
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("buildType", buildType);
        editor.putString("orientation", orientation);
        editor.putString("deviceId", deviceId);
        editor.apply();
        
        // Save device ID using DeviceIdManager if not blank
        if (!deviceId.isEmpty()) {
            deviceIdManager.setDeviceId(deviceId);
        }
        
        Log.i(TAG, "Saved settings - Build: " + buildType + ", Orientation: " + orientation + ", Device: " + deviceId);
        
        // Start download and installation
        downloadAndInstallAPK(buildType);
    }
    
    private void downloadAndInstallAPK(String buildType) {
        Log.i(TAG, "downloadAndInstallAPK() called for buildType: " + buildType);
        statusText.setText("Getting latest release information...");
        saveAndInstallButton.setEnabled(false);
        
        // Proceed with download - separate apps means no version conflicts
        proceedWithDownload(buildType);
    }
    
    private void proceedWithDownload(String buildType) {
        // Get the latest release download URL in background thread
        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting GitHub API call in background thread...");
                runOnUiThread(() -> statusText.setText("Contacting GitHub API..."));
                
                String downloadUrl = getReleaseDownloadUrl(buildType);
                
                // Switch back to UI thread to start download
                runOnUiThread(() -> {
                    if (downloadUrl != null) {
                        Log.i(TAG, "Found " + buildType + " release URL: " + downloadUrl);
                        statusText.setText("Found " + buildType.toLowerCase() + " release, starting download...");
                        startDownload(downloadUrl, buildType);
                    } else {
                        Log.w(TAG, "Could not get latest release URL, using fallback");
                        startDownload(FALLBACK_APK_URL, buildType);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting " + buildType + " release URL", e);
                runOnUiThread(() -> {
                    Log.w(TAG, "Using fallback URL due to error: " + e.getMessage());
                    startDownload(FALLBACK_APK_URL, buildType);
                });
            }
        });
    }
    
    private String getReleaseDownloadUrl(String buildType) {
        try {
            // Choose API URL based on build type
            String apiUrl;
            if ("PreRelease".equals(buildType)) {
                apiUrl = GITHUB_PRERELEASE_URL;
                Log.i(TAG, "Fetching prerelease from GitHub API: " + apiUrl);
            } else {
                apiUrl = GITHUB_LATEST_RELEASE_URL;
                Log.i(TAG, "Fetching latest release from GitHub API: " + apiUrl);
            }
            
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            Log.i(TAG, "GitHub API response code: " + responseCode);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the single release object (not an array)
                return parseReleaseForApk(new JSONObject(response.toString()), buildType);
                
            } else {
                Log.e(TAG, "GitHub API request failed with code: " + responseCode + " for " + buildType);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching " + buildType + " release", e);
            return null;
        }
    }
    
    private String parseReleaseForApk(JSONObject releaseData, String buildType) {
        try {
            String tagName = releaseData.getString("tag_name");
            Log.i(TAG, buildType + " release tag: " + tagName);
            
            // Look for APK asset in the assets array
            JSONArray assets = releaseData.getJSONArray("assets");
            
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.getString("name");
                
                // Look for any APK file
                if (assetName.toLowerCase().endsWith(".apk")) {
                    String downloadUrl = asset.getString("browser_download_url");
                    Log.i(TAG, "Found " + buildType + " APK asset: " + assetName + " -> " + downloadUrl);
                    return downloadUrl;
                }
            }
            
            Log.w(TAG, "No suitable APK assets found in " + buildType + " release");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + buildType + " release data", e);
            return null;
        }
    }
    private void downloadAPKDirectly(String apkUrl, String buildType) {
        try {
            // Use app's external files directory
            File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                runOnUiThread(() -> {
                    statusText.setText("Error: External files directory not available");
                    saveAndInstallButton.setEnabled(true);
                });
                return;
            }
            
            File downloadFile = new File(externalFilesDir, "kiosk-update.apk");
            
            // Delete existing file if it exists
            if (downloadFile.exists()) {
                boolean deleted = downloadFile.delete();
                Log.i(TAG, "Existing APK file deleted: " + deleted);
            }
            
            runOnUiThread(() -> statusText.setText("Starting direct download..."));
            
            java.net.URL url = new java.net.URL(apkUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
            connection.setRequestProperty("User-Agent", "KIDSKioskApp/1.0");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            Log.i(TAG, "Starting direct download from URL: " + apkUrl);
            Log.i(TAG, "Download destination: " + downloadFile.getAbsolutePath());
            
            int responseCode = connection.getResponseCode();
            if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error: " + responseCode);
            }
            
            long contentLength = connection.getContentLengthLong();
            Log.i(TAG, "Expected download size: " + contentLength + " bytes");
            
            try (java.io.InputStream inputStream = connection.getInputStream();
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(downloadFile);
                 java.io.BufferedInputStream bufferedInput = new java.io.BufferedInputStream(inputStream);
                 java.io.BufferedOutputStream bufferedOutput = new java.io.BufferedOutputStream(outputStream)) {
                
                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                int bytesRead;
                long lastUpdate = 0;
                
                while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                    bufferedOutput.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Update UI every 500ms
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdate > 500) {
                        final long finalTotalBytes = totalBytesRead;
                        final long finalContentLength = contentLength;
                        runOnUiThread(() -> {
                            if (finalContentLength > 0) {
                                int progress = (int) ((finalTotalBytes * 100) / finalContentLength);
                                statusText.setText("Downloading... " + progress + "% (" + (finalTotalBytes / 1024) + " KB)");
                            } else {
                                statusText.setText("Downloading... " + (finalTotalBytes / 1024) + " KB");
                            }
                        });
                        lastUpdate = currentTime;
                    }
                }
                
                // Ensure all data is written to disk
                bufferedOutput.flush();
                outputStream.getFD().sync();
            }
            
            connection.disconnect();
            
            long finalFileSize = downloadFile.length();
            Log.i(TAG, "Download completed. Final file size: " + finalFileSize + " bytes");
            
            if (contentLength > 0 && finalFileSize != contentLength) {
                Log.w(TAG, "File size mismatch: expected " + contentLength + ", got " + finalFileSize);
            }
            
            // Also copy the downloaded APK to the public Downloads folder so it can be accessed
            // from adb and for easier manual inspection. Keep the original in the app's external files.
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                File publicApk = new File(downloadsDir, "kiosk-update.apk");

                try (java.io.FileInputStream fis = new java.io.FileInputStream(downloadFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(publicApk)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }

                // Make file world-readable so adb or other callers can read it
                publicApk.setReadable(true, false);
                final long publicSize = publicApk.length();
                final String publicPath = publicApk.getAbsolutePath();
                Log.i(TAG, "Copied APK to public Downloads: " + publicPath + " (" + publicSize + " bytes)");

                runOnUiThread(() -> {
                    statusText.setText("Download completed! Saved to: " + publicPath + "\nSize: " + (publicSize / 1024) + " KB\nVerifying file...");
                    verifyWithRetry(3);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to copy APK to public Downloads", e);
                runOnUiThread(() -> {
                    statusText.setText("Download completed! Verifying file...");
                    verifyWithRetry(3);
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Direct download failed", e);
            runOnUiThread(() -> {
                statusText.setText("Download failed: " + e.getMessage());
                saveAndInstallButton.setEnabled(true);
            });
        }
    }

    private void startDownload(String apkUrl, String buildType) {
        statusText.setText("Downloading " + buildType + " APK...");
        
        // Use direct download instead of DownloadManager for more reliable file writing
        new Thread(() -> {
            downloadAPKDirectly(apkUrl, buildType);
        }).start();
    }

    private void verifyWithRetry(int attemptsLeft) {
        if (attemptsLeft <= 0) {
            Log.e(TAG, "All verification attempts failed - file may be corrupted");
            statusText.setText("Download verification failed after multiple attempts.");
            saveAndInstallButton.setEnabled(true);
            return;
        }
        
        Log.i(TAG, "Attempting file verification (attempts left: " + attemptsLeft + ")");
        
        if (verifyDownloadedAPK()) {
            // Display file information before installation
            displayDownloadedFileInfo();
            new android.os.Handler().postDelayed(() -> installAPK(), 500);
        } else {
            Log.w(TAG, "Verification attempt failed, retrying in 500ms");
            statusText.setText("Verifying download... (attempt " + (4 - attemptsLeft) + " of 3)");
            new android.os.Handler().postDelayed(() -> verifyWithRetry(attemptsLeft - 1), 500);
        }
    }

    private boolean verifyDownloadedAPK() {
        try {
            File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                Log.e(TAG, "External files directory not available for verification");
                return false;
            }
            
            File downloadFile = new File(externalFilesDir, "kiosk-update.apk");
            
            if (!downloadFile.exists()) {
                Log.e(TAG, "Downloaded APK file does not exist: " + downloadFile.getAbsolutePath());
                
                // List all files in the directory to debug
                File[] files = externalFilesDir.listFiles();
                if (files != null) {
                    Log.i(TAG, "Files in downloads directory:");
                    for (File file : files) {
                        Log.i(TAG, "  - " + file.getName() + " (" + file.length() + " bytes)");
                    }
                } else {
                    Log.e(TAG, "Downloads directory is null or empty");
                }
                return false;
            }
            
            long fileSize = downloadFile.length();
            Log.i(TAG, "Downloaded APK file size: " + fileSize + " bytes");
            
            // Also check if file is readable
            if (!downloadFile.canRead()) {
                Log.e(TAG, "Downloaded APK file cannot be read");
                return false;
            }
            
            // Check if file size is reasonable (APK should be at least 1MB and match expected size)
            if (fileSize < 1024 * 1024) {
                Log.e(TAG, "Downloaded APK file too small: " + fileSize + " bytes");
                return false;
            }
            
            // Check if file size is reasonable but not too large (should be under 20MB)
            if (fileSize > 20 * 1024 * 1024) {
                Log.e(TAG, "Downloaded APK file too large: " + fileSize + " bytes (may be corrupted)");
                return false;
            }
            
            Log.i(TAG, "APK file size validation passed: " + fileSize + " bytes");
            
            // Verify it's a valid ZIP file by checking the header
            try (FileInputStream fis = new FileInputStream(downloadFile)) {
                byte[] header = new byte[4];
                int bytesRead = fis.read(header);
                if (bytesRead < 4) {
                    Log.e(TAG, "Cannot read APK file header");
                    return false;
                }
                
                // Check for ZIP signature (PK\003\004 or PK\005\006)
                if (!(header[0] == 'P' && header[1] == 'K' && 
                      (header[2] == 0x03 || header[2] == 0x05))) {
                    Log.e(TAG, "APK file header invalid - not a ZIP file");
                    return false;
                }
                
                Log.i(TAG, "APK file verification passed - valid ZIP header and reasonable size");
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying downloaded APK", e);
            return false;
        }
    }

    private void displayDownloadedFileInfo() {
        try {
            File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                statusText.setText("File verified! Starting installation...");
                return;
            }
            
            File downloadFile = new File(externalFilesDir, "kiosk-update.apk");
            
            if (!downloadFile.exists()) {
                statusText.setText("File verified! Starting installation...");
                return;
            }
            
            long fileSize = downloadFile.length();
            String filePath = downloadFile.getAbsolutePath();
            
            // Format file size in a human-readable format
            String formattedSize;
            if (fileSize >= 1024 * 1024) {
                formattedSize = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
            } else if (fileSize >= 1024) {
                formattedSize = String.format("%.2f KB", fileSize / 1024.0);
            } else {
                formattedSize = fileSize + " bytes";
            }
            
            Log.i(TAG, "Downloaded APK info - Location: " + filePath + ", Size: " + fileSize + " bytes (" + formattedSize + ")");
            
            statusText.setText("File verified!\nLocation: " + filePath + "\nSize: " + formattedSize + "\nStarting installation...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting downloaded file info", e);
            statusText.setText("File verified! Starting installation...");
        }
    }

    private void installAPK() {
        // Show confirmation dialog before installation
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Install Update");
        builder.setMessage("Ready to install the update. The app will close after starting the installation. Continue?");
        builder.setPositiveButton("Install", (dialog, which) -> {
            proceedWithInstallation();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            statusText.setText("Installation cancelled");
            saveAndInstallButton.setEnabled(true);
        });
        
        AlertDialog dialog = builder.create();
        
        // Position dialog at the top of the screen so file info remains visible
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            layoutParams.y = 100; // Offset from top in pixels
            window.setAttributes(layoutParams);
        }
        
        dialog.show();
    }
    
    private void proceedWithInstallation() {
        // Check if we have permission to install unknown apps (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Log.w(TAG, "App does not have permission to install unknown apps");
                
                // For Android TV, we'll show a more TV-friendly dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Installation Permission Required");
                builder.setMessage("This app needs permission to install updates. Please grant permission in the next screen, then return to try again.");
                builder.setPositiveButton("Grant Permission", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, 1000);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not open install permission settings", e);
                        // Fallback for Android TV - try general security settings
                        try {
                            Intent fallbackIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                            startActivity(fallbackIntent);
                            Toast.makeText(this, "Please enable 'Unknown sources' for this app", Toast.LENGTH_LONG).show();
                        } catch (Exception e2) {
                            Log.e(TAG, "Could not open security settings either", e2);
                            statusText.setText("Please enable install permission in device settings");
                        }
                    }
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    statusText.setText("Installation cancelled - permission required");
                    saveAndInstallButton.setEnabled(true);
                });
                builder.show();
                return;
            }
        }
        
        try {
            // Use the same location as download - app's external files directory
            File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                String errorMsg = "Error: External files directory not available";
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg);
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            File apkFile = new File(externalFilesDir, "kiosk-update.apk");
            
            Log.i(TAG, "Checking for APK file at: " + apkFile.getAbsolutePath());
            
            if (!apkFile.exists()) {
                String errorMsg = "Error: Downloaded APK not found at " + apkFile.getAbsolutePath();
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg);
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            if (!apkFile.canRead()) {
                String errorMsg = "Error: Cannot read APK file (permission issue)";
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg);
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            long fileSize = apkFile.length();
            Log.i(TAG, "APK file size: " + fileSize + " bytes");
            
            if (fileSize < 1000) { // Less than 1KB probably indicates an error page
                String errorMsg = "Error: APK file too small (" + fileSize + " bytes) - likely download failed";
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg);
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            // Use the public Downloads copy for installation (more compatible)
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File publicApk = new File(downloadsDir, "kiosk-update.apk");
            
            if (!publicApk.exists()) {
                String errorMsg = "Error: Public APK copy not found at " + publicApk.getAbsolutePath();
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg);
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.i(TAG, "Using public APK file: " + publicApk.getAbsolutePath() + " (" + publicApk.length() + " bytes)");
            
            // Try multiple installation approaches
            boolean installSuccess = false;
            
            // Strategy 1: Use ACTION_INSTALL_PACKAGE (Android 14+ preferred method)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && !installSuccess) {
                try {
                    Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                        this, 
                        "com.kidsim.tvkiosk.fileprovider", 
                        publicApk
                    );
                    installIntent.setData(apkUri);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                    installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, getPackageName());
                    
                    Log.i(TAG, "Strategy 1: ACTION_INSTALL_PACKAGE with FileProvider");
                    if (installIntent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(installIntent, 2000);
                        installSuccess = true;
                    } else {
                        Log.w(TAG, "ACTION_INSTALL_PACKAGE not available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Strategy 1 (ACTION_INSTALL_PACKAGE) failed: " + e.getMessage(), e);
                }
            }
            
            // Strategy 2: Use ACTION_VIEW with direct file URI (older Android/TV compatibility)
            if (!installSuccess) {
                try {
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    Uri apkUri = Uri.fromFile(publicApk);
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    Log.i(TAG, "Strategy 2: ACTION_VIEW with file URI: " + apkUri.toString());
                    if (installIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(installIntent);
                        installSuccess = true;
                    } else {
                        Log.w(TAG, "ACTION_VIEW with file URI not available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Strategy 2 (ACTION_VIEW file URI) failed: " + e.getMessage(), e);
                }
            }
            
            // Strategy 3: Use PackageInstaller (programmatic install)
            if (!installSuccess && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    android.content.pm.PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
                    android.content.pm.PackageInstaller.SessionParams params = 
                        new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(getPackageName());
                    
                    int sessionId = packageInstaller.createSession(params);
                    android.content.pm.PackageInstaller.Session session = packageInstaller.openSession(sessionId);
                    
                    java.io.OutputStream out = session.openWrite("COSU", 0, -1);
                    java.io.FileInputStream in = new java.io.FileInputStream(publicApk);
                    byte[] buffer = new byte[65536];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                    in.close();
                    out.close();
                    
                    Intent intent = new Intent(this, UpdateActivity.class);
                    android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE);
                    android.content.IntentSender statusReceiver = pendingIntent.getIntentSender();
                    session.commit(statusReceiver);
                    
                    Log.i(TAG, "Strategy 3: PackageInstaller programmatic install");
                    installSuccess = true;
                } catch (Exception e) {
                    Log.e(TAG, "Strategy 3 (PackageInstaller) failed: " + e.getMessage(), e);
                }
            }
            
            if (!installSuccess) {
                // Strategy 4: Legacy method for very old Android versions
                try {
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(Uri.fromFile(publicApk), "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    Log.i(TAG, "Strategy 4: Legacy ACTION_VIEW");
                    startActivity(installIntent);
                    installSuccess = true;
                } catch (Exception e) {
                    Log.e(TAG, "Strategy 4 (Legacy) failed: " + e.getMessage(), e);
                }
            }
            
            if (installSuccess) {
                statusText.setText("Installation started. Please follow the installation prompts on screen.");
                Log.i(TAG, "APK installation initiated successfully");
                
                // Don't close the app immediately - let the user see the status
                // The installation will happen in the background
                // App will be replaced when installation completes
            } else {
                String errorMsg = "All installation methods failed. Please install manually from Downloads folder.";
                Log.e(TAG, errorMsg);
                statusText.setText(errorMsg + "\nFile: " + publicApk.getAbsolutePath());
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            String errorMsg = "Error installing APK: " + e.getMessage();
            statusText.setText(errorMsg);
            saveAndInstallButton.setEnabled(true);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1000) { // Install permission request
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    Log.i(TAG, "Install permission granted, proceeding with installation");
                    statusText.setText("Permission granted, installing APK...");
                    // Retry installation now that we have permission
                    proceedWithInstallation();
                } else {
                    Log.w(TAG, "Install permission denied");
                    statusText.setText("Installation failed - permission denied");
                    saveAndInstallButton.setEnabled(true);
                    Toast.makeText(this, "Installation permission is required to update the app", Toast.LENGTH_LONG).show();
                }
            }
        }
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
     * Exit the application completely
     */
    private void exitApp() {
        Log.i(TAG, "Exiting application");
        
        // Save any pending preferences first
        saveCurrentSettings();
        
        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("Exit Application")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Yes", (dialog, which) -> {
                finishAffinity(); // Close all activities
                System.exit(0);   // Terminate the app process
            })
            .setNegativeButton("No", null)
            .show();
    }
    
    /**
     * Save current settings without performing update
     */
    private void saveCurrentSettings() {
        try {
            // Get selected values
            String orientation = orientationSpinner.getSelectedItem() != null ? 
                orientationSpinner.getSelectedItem().toString() : "Landscape";
            String deviceId = deviceIdSpinner.getSelectedItem() != null ? 
                deviceIdSpinner.getSelectedItem().toString() : "";
            String buildType = "Release"; // Unified app
            
            // Save preferences
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("buildType", buildType);
            editor.putString("orientation", orientation);
            editor.putString("deviceId", deviceId);
            editor.apply();
            
            // Save device ID using DeviceIdManager if not blank
            if (!deviceId.isEmpty()) {
                deviceIdManager.setDeviceId(deviceId);
            }
            
            Log.i(TAG, "Settings saved - Build: " + buildType + ", Orientation: " + orientation + ", Device: " + deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Error saving settings", e);
        }
    }
}
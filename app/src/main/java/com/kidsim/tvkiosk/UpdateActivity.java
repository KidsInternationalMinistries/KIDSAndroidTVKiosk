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
    
    // GitHub API URLs to get release information by tag (avoids pagination issues)
    private static final String GITHUB_TAG_RELEASE_BASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tags/";
    private static final String GITHUB_DEBUG_TAG_URL = GITHUB_TAG_RELEASE_BASE_URL + "debug";
    private static final String GITHUB_LATEST_TAG_URL = GITHUB_TAG_RELEASE_BASE_URL + "v1.7"; // Will be updated dynamically
    
    // Fallback URLs in case API fails
    private static final String FALLBACK_RELEASE_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/download/v1.7/app-release.apk";
    private static final String FALLBACK_DEBUG_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/download/debug/app-debug.apk";
    
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
        kioskButton = findViewById(R.id.kioskButton);
        exitButton = findViewById(R.id.exitButton);
        
        // Start with update button disabled until all selections are made
        saveAndInstallButton.setEnabled(false);
        
        // Set button text based on app type
        updateButtonText();
        
        // Display current version
        displayCurrentVersion();
    }
    
    private void updateButtonText() {
        if (isDebugApp()) {
            saveAndInstallButton.setText("Update");
        } else {
            saveAndInstallButton.setText("Update");
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
        
        // Make sure the button visual state updates properly
        saveAndInstallButton.refreshDrawableState();
        
        String appType = isDebugApp() ? "Debug" : "Release";
        Log.d(TAG, "Button enabled: " + hasValidSelections + 
               " (App Type: " + appType + " (auto)" +
               ", Orientation: " + (orientationSpinner.getSelectedItem() != null ? orientationSpinner.getSelectedItem().toString() : "null") +
               ", DeviceId: " + (deviceIdSpinner.getSelectedItem() != null ? deviceIdSpinner.getSelectedItem().toString() : "null") + ")");
    }
    
    private void saveAndInstall() {
        Log.i(TAG, "saveAndInstall() called - Save and Install button clicked");
        
        // Get selected values
        String orientation = orientationSpinner.getSelectedItem().toString();
        String deviceId = deviceIdSpinner.getSelectedItem().toString();
        
        // App automatically knows its build type
        String buildType = isDebugApp() ? "Debug" : "Release";
        
        Log.i(TAG, "Selected values - BuildType: " + buildType + " (auto-detected), Orientation: " + orientation + ", DeviceId: " + deviceId);
        
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
                        Log.w(TAG, "Could not get " + buildType + " release URL, using fallback");
                        String fallbackUrl = buildType.equals("Debug") ? FALLBACK_DEBUG_APK_URL : FALLBACK_RELEASE_APK_URL;
                        startDownload(fallbackUrl, buildType);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting " + buildType + " release URL", e);
                runOnUiThread(() -> {
                    Log.w(TAG, "Using fallback URL due to error: " + e.getMessage());
                    String fallbackUrl = buildType.equals("Debug") ? FALLBACK_DEBUG_APK_URL : FALLBACK_RELEASE_APK_URL;
                    startDownload(fallbackUrl, buildType);
                });
            }
        });
    }
    
    private String getReleaseDownloadUrl(String buildType) {
        try {
            // Use tag-based API to avoid pagination issues
            String apiUrl = buildType.equals("Release") ? GITHUB_LATEST_TAG_URL : GITHUB_DEBUG_TAG_URL;
            Log.i(TAG, "Fetching " + buildType + " release by tag from GitHub API: " + apiUrl);
            
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
                Log.e(TAG, "GitHub API request failed with code: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching " + buildType + " release by tag", e);
            return null;
        }
    }
    
    private String parseReleaseForApk(JSONObject releaseData, String buildType) {
        try {
            String tagName = releaseData.getString("tag_name");
            Log.i(TAG, buildType + " release tag: " + tagName);
            
            // Look for APK asset in the assets array
            JSONArray assets = releaseData.getJSONArray("assets");
            String fallbackApkUrl = null;
            
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.getString("name");
                
                // For Release builds, prefer app-release.apk, otherwise any APK
                if (assetName.toLowerCase().endsWith(".apk")) {
                    String downloadUrl = asset.getString("browser_download_url");
                    
                    if (buildType.equals("Release")) {
                        // Prefer release APKs for release builds
                        if (assetName.toLowerCase().contains("release") || 
                            (!assetName.toLowerCase().contains("debug"))) {
                            Log.i(TAG, "Found " + buildType + " APK asset: " + assetName + " -> " + downloadUrl);
                            return downloadUrl;
                        }
                        // Keep any APK as fallback for Release builds
                        if (fallbackApkUrl == null) {
                            fallbackApkUrl = downloadUrl;
                            Log.d(TAG, "Keeping fallback APK for " + buildType + ": " + assetName);
                        }
                    } else {
                        // For non-release builds, take first APK found
                        Log.i(TAG, "Found " + buildType + " APK asset: " + assetName + " -> " + downloadUrl);
                        return downloadUrl;
                    }
                }
            }
            
            // If Release build and no preferred APK found, use fallback
            if (buildType.equals("Release") && fallbackApkUrl != null) {
                Log.i(TAG, "Using fallback APK for " + buildType + ": " + fallbackApkUrl);
                return fallbackApkUrl;
            }
            
            Log.w(TAG, "No suitable APK assets found in " + buildType + " release");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + buildType + " release data", e);
            return null;
        }
    }
    
    private String findDebugReleaseApk(JSONArray releases) {
        try {
            // Look through releases to find the latest debug/pre-release
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tagName = release.getString("tag_name");
                boolean isPrerelease = release.optBoolean("prerelease", false);
                boolean isDraft = release.optBoolean("draft", false);
                
                // Skip drafts
                if (isDraft) {
                    continue;
                }
                
                // Look for debug releases (pre-releases or tags containing "debug")
                if (isPrerelease || tagName.toLowerCase().contains("debug")) {
                    Log.i(TAG, "Found debug release candidate: " + tagName + " (prerelease: " + isPrerelease + ")");
                    
                    JSONArray assets = release.getJSONArray("assets");
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String assetName = asset.getString("name");
                        
                        // Look for debug APKs first, then any APK
                        if (assetName.toLowerCase().endsWith(".apk")) {
                            if (assetName.toLowerCase().contains("debug") || 
                                assetName.toLowerCase().contains("test") ||
                                !assetName.toLowerCase().contains("release")) {
                                String downloadUrl = asset.getString("browser_download_url");
                                Log.i(TAG, "Found Debug APK asset: " + assetName + " -> " + downloadUrl);
                                return downloadUrl;
                            }
                        }
                    }
                }
            }
            
            Log.w(TAG, "No debug releases found, trying any recent release with debug APK");
            
            // Fallback: look for any recent release that has a debug APK
            for (int i = 0; i < Math.min(5, releases.length()); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tagName = release.getString("tag_name");
                boolean isDraft = release.optBoolean("draft", false);
                
                if (isDraft) continue;
                
                JSONArray assets = release.getJSONArray("assets");
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject asset = assets.getJSONObject(j);
                    String assetName = asset.getString("name");
                    
                    if (assetName.toLowerCase().endsWith(".apk") && 
                        (assetName.toLowerCase().contains("debug") || assetName.toLowerCase().contains("test"))) {
                        String downloadUrl = asset.getString("browser_download_url");
                        Log.i(TAG, "Found Debug APK in release " + tagName + ": " + assetName + " -> " + downloadUrl);
                        return downloadUrl;
                    }
                }
            }
            
            Log.w(TAG, "No debug APK found in recent releases");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding debug release", e);
            return null;
        }
    }
    
    private void startDownload(String apkUrl, String buildType) {
        statusText.setText("Downloading " + buildType + " APK...");
        
        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            
            if (downloadManager == null) {
                throw new Exception("DownloadManager service not available");
            }
            
            Uri downloadUri = Uri.parse(apkUrl);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            
            // Use app's external files directory instead of public Downloads
            // This doesn't require MANAGE_EXTERNAL_STORAGE permission on Android 11+
            File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                throw new Exception("External files directory not available");
            }
            
            File downloadFile = new File(externalFilesDir, "kiosk-update.apk");
            
            // Delete existing file if it exists
            if (downloadFile.exists()) {
                downloadFile.delete();
            }
            
            request.setTitle("Kiosk App Update");
            request.setDescription("Downloading " + buildType + " version from GitHub");
            request.setDestinationUri(Uri.fromFile(downloadFile));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(false);
            
            Log.i(TAG, "Starting download from URL: " + apkUrl);
            downloadId = downloadManager.enqueue(request);
            Log.i(TAG, "Download started with ID: " + downloadId);
            
            // Start a timer to check download status periodically
            checkDownloadStatusPeriodically(downloadManager, downloadId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            statusText.setText("Error starting download: " + e.getMessage());
            saveAndInstallButton.setEnabled(true);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void checkDownloadStatusPeriodically(DownloadManager downloadManager, long downloadId) {
        android.os.Handler handler = new android.os.Handler();
        Runnable statusChecker = new Runnable() {
            @Override
            public void run() {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                
                android.database.Cursor cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    
                    if (statusIndex >= 0) {
                        int status = cursor.getInt(statusIndex);
                        long bytesDownloaded = bytesDownloadedIndex >= 0 ? cursor.getLong(bytesDownloadedIndex) : 0;
                        long totalSize = totalSizeIndex >= 0 ? cursor.getLong(totalSizeIndex) : 0;
                        
                        Log.i(TAG, "Download status check - Status: " + status + ", Downloaded: " + bytesDownloaded + "/" + totalSize + " bytes");
                        
                        switch (status) {
                            case DownloadManager.STATUS_PENDING:
                                statusText.setText("Download pending...");
                                handler.postDelayed(this, 2000); // Check again in 2 seconds
                                break;
                            case DownloadManager.STATUS_RUNNING:
                                statusText.setText("Download in progress... " + (bytesDownloaded / 1024) + " KB");
                                handler.postDelayed(this, 2000); // Check again in 2 seconds
                                break;
                            case DownloadManager.STATUS_SUCCESSFUL:
                                Log.i(TAG, "Download completed successfully - triggering installation");
                                statusText.setText("Download completed! Starting installation...");
                                // Give a brief moment for UI to update, then start installation
                                handler.postDelayed(() -> installAPK(), 500);
                                break;
                            case DownloadManager.STATUS_FAILED:
                                int reason = reasonIndex >= 0 ? cursor.getInt(reasonIndex) : -1;
                                String reasonStr = getDownloadFailureReason(reason);
                                Log.e(TAG, "Download failed: " + reasonStr);
                                statusText.setText("Download failed: " + reasonStr);
                                saveAndInstallButton.setEnabled(true);
                                break;
                            case DownloadManager.STATUS_PAUSED:
                                statusText.setText("Download paused...");
                                handler.postDelayed(this, 2000); // Check again in 2 seconds
                                break;
                            default:
                                Log.w(TAG, "Unknown download status: " + status);
                                statusText.setText("Download status: " + status);
                                handler.postDelayed(this, 2000); // Check again in 2 seconds
                                break;
                        }
                    }
                    cursor.close();
                } else {
                    Log.e(TAG, "Could not query download status - download may have been removed");
                    statusText.setText("Error: Could not check download status");
                    saveAndInstallButton.setEnabled(true);
                }
            }
        };
        
        // Start checking after 2 seconds
        handler.postDelayed(statusChecker, 2000);
    }

    private String getDownloadFailureReason(int reasonCode) {
        switch (reasonCode) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Cannot resume download";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Storage device not found";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File already exists";
            case DownloadManager.ERROR_FILE_ERROR:
                return "Storage error";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP data error";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Too many redirects";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Unhandled HTTP error";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Unknown error (code: " + reasonCode + ")";
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
        builder.show();
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
            
            // Install APK using a multi-step approach for maximum compatibility
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = null;
            
            // Strategy 1: Try FileProvider first (most secure)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    apkUri = androidx.core.content.FileProvider.getUriForFile(
                        this, 
                        getFileProviderAuthority(), 
                        apkFile
                    );
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    
                    Log.i(TAG, "Strategy 1: Using FileProvider URI: " + apkUri.toString());
                    Log.i(TAG, "FileProvider authority: " + getFileProviderAuthority());
                    
                    // Test if this intent can be resolved
                    if (installIntent.resolveActivity(getPackageManager()) != null) {
                        Log.i(TAG, "FileProvider intent can be resolved, proceeding");
                    } else {
                        Log.w(TAG, "FileProvider intent cannot be resolved, trying fallback");
                        throw new Exception("FileProvider intent not resolvable");
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Strategy 1 failed: " + e.getMessage(), e);
                    
                    // Strategy 2: Copy to public downloads and use direct file URI
                    try {
                        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs();
                        }
                        
                        File publicApk = new File(downloadsDir, "kiosk-update-" + System.currentTimeMillis() + ".apk");
                        
                        // Copy APK to public location
                        java.io.FileInputStream fis = new java.io.FileInputStream(apkFile);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(publicApk);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        fis.close();
                        fos.close();
                        
                        // Make file readable
                        publicApk.setReadable(true, false);
                        
                        apkUri = Uri.fromFile(publicApk);
                        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        
                        Log.i(TAG, "Strategy 2: Copied APK to public location: " + publicApk.getAbsolutePath());
                        Log.i(TAG, "Using file URI: " + apkUri.toString());
                        
                    } catch (Exception e2) {
                        Log.e(TAG, "Strategy 2 failed: " + e2.getMessage(), e2);
                        
                        // Strategy 3: Last resort - direct file URI from original location
                        apkUri = Uri.fromFile(apkFile);
                        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        Log.i(TAG, "Strategy 3: Using direct file URI: " + apkUri.toString());
                    }
                }
            } else {
                // For older Android versions, use direct file URI
                apkUri = Uri.fromFile(apkFile);
                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                Log.i(TAG, "Legacy: Using direct file URI for older Android: " + apkUri.toString());
            }
            
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            statusText.setText("Installing APK...");
            Log.i(TAG, "Starting APK installation for file: " + apkFile.getAbsolutePath());
            
            // Check if there's an activity that can handle the install intent
            if (installIntent.resolveActivity(getPackageManager()) != null) {
                
                // Show final message to user
                statusText.setText("Installation started. App will close now. Please follow the installation prompts.");
                
                // Start the installation
                startActivity(installIntent);
                
                // Give user time to read the message, then close the app
                new android.os.Handler().postDelayed(() -> {
                    // Close this activity and the entire application
                    finishAffinity(); // This closes all activities in the app
                    System.exit(0);   // Fully terminate the app process
                }, 2000); // Wait 2 seconds
                
            } else {
                Log.e(TAG, "No activity found to handle APK installation intent");
                statusText.setText("Error: Device cannot install APK files");
                saveAndInstallButton.setEnabled(true);
                Toast.makeText(this, "Device does not support APK installation", Toast.LENGTH_LONG).show();
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
     * Get the correct FileProvider authority based on the build type
     */
    private String getFileProviderAuthority() {
        // Check if this is a debug build by looking at the package name
        String packageName = getPackageName();
        if (packageName.endsWith(".debug")) {
            return "com.kidsim.tvkiosk.debug.fileprovider";
        } else {
            return "com.kidsim.tvkiosk.fileprovider";
        }
    }
    
    /**
     * Check if this is the debug app (separate package)
     */
    private boolean isDebugApp() {
        return getPackageName().endsWith(".debug");
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
            String buildType = isDebugApp() ? "Debug" : "Release";
            
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
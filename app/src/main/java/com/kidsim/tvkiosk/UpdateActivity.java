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
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends Activity {
    private static final String TAG = "UpdateActivity";
    
    // GitHub URLs for APK downloads
    // Note: These URLs point to GitHub Releases where APK files are uploaded
    private static final String GITHUB_DEBUG_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest/download/app-test.apk";
    private static final String GITHUB_RELEASE_APK_URL = 
        "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest/download/app-test.apk";
    
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
    private Button saveAndInstallButton;
    
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
        
        // Register download receiver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
                Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            // Receiver may not be registered
        }
    }
    
    private void initializeViews() {
        buildTypeSpinner = findViewById(R.id.buildTypeSpinner);
        orientationSpinner = findViewById(R.id.orientationSpinner);
        deviceIdSpinner = findViewById(R.id.deviceIdSpinner);
        statusText = findViewById(R.id.statusText);
        saveAndInstallButton = findViewById(R.id.saveAndInstallButton);
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
        saveAndInstallButton.setOnClickListener(v -> saveAndInstall());
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
        boolean hasValidSelections = 
            buildTypeSpinner.getSelectedItem() != null &&
            orientationSpinner.getSelectedItem() != null &&
            deviceIdSpinner.getSelectedItem() != null &&
            !deviceIdSpinner.getSelectedItem().toString().isEmpty();
        
        saveAndInstallButton.setEnabled(hasValidSelections);
    }
    
    private void saveAndInstall() {
        // Get selected values
        String buildType = buildTypeSpinner.getSelectedItem().toString();
        String orientation = orientationSpinner.getSelectedItem().toString();
        String deviceId = deviceIdSpinner.getSelectedItem().toString();
        
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
        statusText.setText("Downloading " + buildType + " APK...");
        saveAndInstallButton.setEnabled(false);
        
        // Determine APK URL based on build type
        String apkUrl = buildType.equals("Debug") ? GITHUB_DEBUG_APK_URL : GITHUB_RELEASE_APK_URL;
        
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            statusText.setText("Error starting download: " + e.getMessage());
            saveAndInstallButton.setEnabled(true);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                // Check download status
                DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                
                android.database.Cursor cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    
                    if (statusIndex >= 0) {
                        int status = cursor.getInt(statusIndex);
                        
                        switch (status) {
                            case DownloadManager.STATUS_SUCCESSFUL:
                                statusText.setText("Download completed. Installing...");
                                installAPK();
                                break;
                                
                            case DownloadManager.STATUS_FAILED:
                                String reason = "Unknown error";
                                if (reasonIndex >= 0) {
                                    int reasonCode = cursor.getInt(reasonIndex);
                                    reason = getDownloadFailureReason(reasonCode);
                                }
                                
                                String errorMsg = "Download failed: " + reason;
                                Log.e(TAG, errorMsg + " (reason code: " + (reasonIndex >= 0 ? cursor.getInt(reasonIndex) : "unknown") + ")");
                                statusText.setText(errorMsg);
                                saveAndInstallButton.setEnabled(true);
                                Toast.makeText(UpdateActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                break;
                                
                            default:
                                Log.w(TAG, "Download completed with status: " + status);
                                statusText.setText("Download completed with status: " + status);
                                saveAndInstallButton.setEnabled(true);
                                break;
                        }
                    } else {
                        Log.e(TAG, "Could not get download status");
                        statusText.setText("Error: Could not check download status");
                        saveAndInstallButton.setEnabled(true);
                    }
                    cursor.close();
                } else {
                    Log.e(TAG, "Could not query download status");
                    statusText.setText("Error: Could not query download status");
                    saveAndInstallButton.setEnabled(true);
                }
            }
        }
    };
    
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
            
            // Install APK using FileProvider for Android 11+ compatibility
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7+ (required for security)
                Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                    this, 
                    "com.kidsim.tvkiosk.fileprovider", 
                    apkFile
                );
                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Legacy approach for older Android versions
                installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            statusText.setText("Installing APK...");
            Log.i(TAG, "Starting APK installation for file: " + apkFile.getAbsolutePath());
            startActivity(installIntent);
            
            // Close this activity after starting installation
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            String errorMsg = "Error installing APK: " + e.getMessage();
            statusText.setText(errorMsg);
            saveAndInstallButton.setEnabled(true);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }
    }
}
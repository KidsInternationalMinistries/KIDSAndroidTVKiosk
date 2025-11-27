package com.kidsim.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class UpdaterActivity extends Activity {
    private static final String TAG = "UpdaterActivity";
    private static final String KIOSK_PACKAGE_NAME = "com.kidsim.tvkiosk";
    private static final String GITHUB_LATEST_RELEASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest";
    private static final String GITHUB_PRERELEASE_URL = 
        "https://api.github.com/repos/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/tags/prerelease";
    
    private TextView statusText;
    private Button installCurrentButton;
    private Button installPreReleaseButton;
    private Button exitButton;
    private ExecutorService executor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updater);
        
        Log.i(TAG, "KidsIM Kiosk Updater started");
        
        executor = Executors.newSingleThreadExecutor();
        
        initializeViews();
        checkKioskAppStatus();
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
        installCurrentButton = findViewById(R.id.installCurrentButton);
        installPreReleaseButton = findViewById(R.id.installPreReleaseButton);
        exitButton = findViewById(R.id.exitButton);
        
        installCurrentButton.setOnClickListener(v -> startUpdateProcess("current"));
        installPreReleaseButton.setOnClickListener(v -> startUpdateProcess("prerelease"));
        exitButton.setOnClickListener(v -> finish());
    }
    
    private void checkKioskAppStatus() {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo(KIOSK_PACKAGE_NAME, 0);
            
            // Kiosk app is installed
            statusText.setText("KidsIM Kiosk app found.\nChoose version to install:");
            installCurrentButton.setText("Install Current Version");
            installPreReleaseButton.setText("Install PreRelease Version");
            installCurrentButton.setEnabled(true);
            installPreReleaseButton.setEnabled(true);
            
        } catch (PackageManager.NameNotFoundException e) {
            // Kiosk app not installed
            statusText.setText("KidsIM Kiosk app not found.\nChoose version to install:");
            installCurrentButton.setText("Install Current Version");
            installPreReleaseButton.setText("Install PreRelease Version");
            installCurrentButton.setEnabled(true);
            installPreReleaseButton.setEnabled(true);
        }
    }
    
    private void startUpdateProcess(String versionType) {
        installCurrentButton.setEnabled(false);
        installPreReleaseButton.setEnabled(false);
        
        String versionText = versionType.equals("prerelease") ? "PreRelease" : "Current";
        statusText.setText("Starting " + versionText + " version installation...");
        
        // Check permissions first
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                requestInstallPermission(versionType);
                return;
            }
        }
        
        // Start the update process
        executor.execute(() -> performUpdate(versionType));
    }
    
    private void requestInstallPermission(String versionType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("This app needs permission to install the kiosk app. Please grant permission in the next screen.");
        builder.setPositiveButton("Grant Permission", (dialog, which) -> {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.putExtra("versionType", versionType);
                startActivityForResult(intent, 1000);
            } catch (Exception e) {
                Log.e(TAG, "Could not open install permission settings", e);
                runOnUiThread(() -> {
                    statusText.setText("Please enable install permission in device settings");
                    installCurrentButton.setEnabled(true);
                    installPreReleaseButton.setEnabled(true);
                });
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            statusText.setText("Permission required to proceed");
            installCurrentButton.setEnabled(true);
            installPreReleaseButton.setEnabled(true);
        });
        builder.show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1000) { // Install permission request
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    Log.i(TAG, "Install permission granted, starting update");
                    String versionType = data != null ? data.getStringExtra("versionType") : "current";
                    executor.execute(() -> performUpdate(versionType));
                } else {
                    runOnUiThread(() -> {
                        statusText.setText("Permission denied - cannot proceed");
                        installCurrentButton.setEnabled(true);
                        installPreReleaseButton.setEnabled(true);
                    });
                }
            }
        }
    }
    
    private void performUpdate(String versionType) {
        try {
            String versionText = versionType.equals("prerelease") ? "PreRelease" : "Current";
            
            // Step 1: Uninstall existing kiosk app if present
            runOnUiThread(() -> statusText.setText("Step 1: Removing old kiosk app..."));
            uninstallKioskApp();
            
            // Step 2: Download kiosk app
            runOnUiThread(() -> statusText.setText("Step 2: Downloading " + versionText + " kiosk app..."));
            String downloadUrl = getReleaseDownloadUrl(versionType);
            
            if (downloadUrl == null) {
                runOnUiThread(() -> {
                    statusText.setText("Failed to get download URL from GitHub");
                    installCurrentButton.setEnabled(true);
                    installPreReleaseButton.setEnabled(true);
                });
                return;
            }
            
            File apkFile = downloadKioskApp(downloadUrl);
            
            if (apkFile == null || !apkFile.exists()) {
                runOnUiThread(() -> {
                    statusText.setText("Failed to download kiosk app");
                    installCurrentButton.setEnabled(true);
                    installPreReleaseButton.setEnabled(true);
                });
                return;
            }
            
            // Step 3: Install new kiosk app
            runOnUiThread(() -> statusText.setText("Step 3: Installing " + versionText + " kiosk app..."));
            installKioskApp(apkFile);
            
        } catch (Exception e) {
            Log.e(TAG, "Update process failed", e);
            runOnUiThread(() -> {
                statusText.setText("Update failed: " + e.getMessage());
                installCurrentButton.setEnabled(true);
                installPreReleaseButton.setEnabled(true);
            });
        }
    }
    
    private void uninstallKioskApp() {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo(KIOSK_PACKAGE_NAME, 0);
            
            // App exists, uninstall it
            Log.i(TAG, "Uninstalling existing kiosk app");
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // Use PackageInstaller for programmatic uninstall
                PackageInstaller packageInstaller = pm.getPackageInstaller();
                packageInstaller.uninstall(KIOSK_PACKAGE_NAME, null);
            }
            
            // Wait a moment for uninstall to complete
            Thread.sleep(2000);
            
            Log.i(TAG, "Kiosk app uninstalled");
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Kiosk app not installed, skipping uninstall");
        } catch (Exception e) {
            Log.w(TAG, "Failed to uninstall kiosk app programmatically", e);
            // Continue anyway - might not be installed or might need manual intervention
        }
    }
    
    private String getReleaseDownloadUrl(String versionType) {
        try {
            String apiUrl = versionType.equals("prerelease") ? GITHUB_PRERELEASE_URL : GITHUB_LATEST_RELEASE_URL;
            String versionText = versionType.equals("prerelease") ? "PreRelease" : "Latest";
            
            Log.i(TAG, "Fetching " + versionText + " release from GitHub API: " + apiUrl);
            
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
                
                // Parse the release object
                JSONObject releaseData = new JSONObject(response.toString());
                String tagName = releaseData.getString("tag_name");
                Log.i(TAG, versionText + " release tag: " + tagName);
                
                // Look for APK asset
                JSONArray assets = releaseData.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.getString("name");
                    
                    if (assetName.toLowerCase().endsWith(".apk")) {
                        String downloadUrl = asset.getString("browser_download_url");
                        Log.i(TAG, "Found " + versionText + " APK asset: " + assetName + " -> " + downloadUrl);
                        return downloadUrl;
                    }
                }
                
                Log.w(TAG, "No APK assets found in " + versionText + " release");
                return null;
                
            } else {
                Log.e(TAG, "GitHub API request failed with code: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching " + versionType + " release", e);
            return null;
        }
    }
    
    private File downloadKioskApp(String downloadUrl) {
        try {
            File downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                Log.e(TAG, "External files directory not available");
                return null;
            }
            
            File apkFile = new File(downloadsDir, "kiosk-app.apk");
            
            // Delete existing file
            if (apkFile.exists()) {
                apkFile.delete();
            }
            
            Log.i(TAG, "Starting download from: " + downloadUrl);
            
            // Extract filename from URL for display
            String fileName = "unknown";
            try {
                String urlPath = new URL(downloadUrl).getPath();
                fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
                if (fileName.isEmpty()) fileName = "kiosk-app.apk";
            } catch (Exception e) {
                fileName = "kiosk-app.apk";
            }
            final String displayFileName = fileName;
            
            runOnUiThread(() -> statusText.setText("Downloading " + displayFileName + "..."));
            
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed with HTTP code: " + responseCode);
                return null;
            }
            
            long contentLength = connection.getContentLengthLong();
            Log.i(TAG, "Expected download size: " + contentLength + " bytes");
            
            try (java.io.InputStream inputStream = connection.getInputStream();
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(apkFile);
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
                                statusText.setText("Downloading " + displayFileName + "...\n" + progress + "% (" + (finalTotalBytes / 1024) + " KB)");
                            } else {
                                statusText.setText("Downloading " + displayFileName + "...\n" + (finalTotalBytes / 1024) + " KB");
                            }
                        });
                        lastUpdate = currentTime;
                    }
                }
                
                bufferedOutput.flush();
                outputStream.getFD().sync();
            }
            
            connection.disconnect();
            
            long finalFileSize = apkFile.length();
            Log.i(TAG, "Download completed. File size: " + finalFileSize + " bytes");
            
            return apkFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return null;
        }
    }
    
    private void installKioskApp(File apkFile) {
        try {
            Log.i(TAG, "Installing kiosk app from: " + apkFile.getAbsolutePath());
            
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                "com.kidsim.updater.fileprovider", 
                apkFile
            );
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            runOnUiThread(() -> {
                statusText.setText("Starting kiosk app installation...\nPlease follow the installation prompts.");
                Toast.makeText(this, "Follow the installation prompts to complete setup", Toast.LENGTH_LONG).show();
                
                startActivity(installIntent);
                
                // Close this updater app after starting the installation
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Log.i(TAG, "Updater app closing after starting installation");
                    finish();
                }, 3000);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start installation", e);
            runOnUiThread(() -> {
                statusText.setText("Installation failed: " + e.getMessage());
                installCurrentButton.setEnabled(true);
                installPreReleaseButton.setEnabled(true);
            });
        }
    }
}
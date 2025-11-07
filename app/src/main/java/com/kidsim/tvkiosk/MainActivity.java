package com.kidsim.tvkiosk;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import com.kidsim.tvkiosk.config.DeviceConfig;
import com.kidsim.tvkiosk.config.PageConfig;
import com.kidsim.tvkiosk.service.WatchdogService;
import java.util.List;

public class MainActivity extends Activity implements ConfigurationManager.ConfigUpdateListener {
    
    private static final String TAG = "MainActivity";
    
    // UI components
    private WebView webView;
    private TextView errorText;
    private Button retryButton;
    
    // Configuration and timing
    private ConfigurationManager configManager;
    private DeviceConfig currentConfig;
    private List<PageConfig> pages;
    private int currentPageIndex = 0;
    
    // Handlers for timing
    private Handler pageHandler;
    private Handler configHandler;
    private Handler retryHandler;
    
    // Runnables
    private Runnable pageRotationRunnable;
    private Runnable configUpdateRunnable;
    private Runnable retryRunnable;
    
    // State tracking
    private boolean isErrorState = false;
    private long lastConfigUpdate = 0;
    private static final long CONFIG_UPDATE_INTERVAL = 3600000; // 1 hour
    private static final long RETRY_INTERVAL = 300000; // 5 minutes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "MainActivity starting");
        
        // Initialize handlers
        pageHandler = new Handler(Looper.getMainLooper());
        configHandler = new Handler(Looper.getMainLooper());
        retryHandler = new Handler(Looper.getMainLooper());
        
        // Setup kiosk mode
        setupKioskMode();
        
        setContentView(R.layout.activity_main);
        
        // Initialize UI components
        initializeViews();
        
        // Initialize configuration manager
        configManager = new ConfigurationManager(this);
        configManager.setConfigUpdateListener(this);
        
        // Load initial configuration
        loadConfiguration();
        
        // Setup periodic configuration updates
        setupConfigurationUpdates();
        
        // Start watchdog service for app stability
        startWatchdogService();
    }
    
    private void setupKioskMode() {
        // Hide system UI for kiosk mode
        hideSystemUI();
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void initializeViews() {
        webView = findViewById(R.id.webView);
        errorText = findViewById(R.id.errorText);
        retryButton = findViewById(R.id.retryButton);
        
        setupWebView();
        setupErrorHandling();
    }
    
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);
        
        // Enable DOM storage
        webSettings.setDomStorageEnabled(true);
        
        // Enable caching but allow cache-busting
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Allow file access
        webSettings.setAllowFileAccess(true);
        
        // Set user agent for better compatibility
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " AndroidTVKiosk/2.0");
        
        // Scale to fit screen
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setSupportZoom(false);
        
        // Set WebView client to handle page loading and errors
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep navigation within the WebView
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded successfully: " + url);
                
                // Hide error state and show WebView
                showWebView();
                
                // Hide system UI again after page load
                hideSystemUI();
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
                
                // Show error state
                showError("Failed to load page: " + description);
            }
        });
    }
    
    private void setupErrorHandling() {
        retryButton.setOnClickListener(v -> {
            Log.i(TAG, "Retry button clicked");
            retryCurrentPage();
        });
    }
    
    private void loadConfiguration() {
        currentConfig = configManager.getCurrentConfig();
        applyConfiguration(currentConfig);
        
        // Try to update from GitHub
        configManager.updateConfigFromGitHub(null);
    }
    
    private void applyConfiguration(DeviceConfig config) {
        Log.i(TAG, "Applying configuration for device: " + config.getDeviceName());
        
        // Apply orientation
        applyOrientation(config.getOrientation());
        
        // Setup pages
        pages = config.getPages();
        if (pages == null || pages.isEmpty()) {
            Log.w(TAG, "No pages configured, using default");
            showError("No pages configured");
            return;
        }
        
        // Start page rotation
        startPageRotation();
    }
    
    private void applyOrientation(String orientation) {
        int orientationValue = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        
        if ("portrait".equalsIgnoreCase(orientation)) {
            orientationValue = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        
        setRequestedOrientation(orientationValue);
        Log.d(TAG, "Set orientation to: " + orientation);
    }
    
    private void startPageRotation() {
        if (pages == null || pages.isEmpty()) {
            Log.w(TAG, "Cannot start page rotation - no pages configured");
            return;
        }
        
        // Stop any existing rotation
        stopPageRotation();
        
        // Reset to first page
        currentPageIndex = 0;
        
        // Load first page
        loadCurrentPage();
        
        // Setup rotation if multiple pages
        if (pages.size() > 1) {
            setupPageRotationTimer();
        }
    }
    
    private void loadCurrentPage() {
        if (pages == null || pages.isEmpty() || currentPageIndex >= pages.size()) {
            Log.w(TAG, "Invalid page index or no pages");
            showError("No valid pages to display");
            return;
        }
        
        PageConfig page = pages.get(currentPageIndex);
        String url = page.getUrl();
        
        Log.i(TAG, "Loading page " + (currentPageIndex + 1) + "/" + pages.size() + ": " + url);
        
        // Add cache-busting parameter if device-level clearCache is enabled
        if (currentConfig != null && currentConfig.isClearCache()) {
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + "_t=" + System.currentTimeMillis();
            Log.d(TAG, "Cache clearing enabled, added timestamp to URL");
        }
        
        webView.loadUrl(url);
    }
    
    private void setupPageRotationTimer() {
        PageConfig currentPage = pages.get(currentPageIndex);
        long displayTime = currentPage.getDisplayTimeSeconds() * 1000L;
        
        pageRotationRunnable = () -> {
            // Move to next page
            currentPageIndex = (currentPageIndex + 1) % pages.size();
            loadCurrentPage();
            
            // Schedule next rotation
            setupPageRotationTimer();
        };
        
        pageHandler.postDelayed(pageRotationRunnable, displayTime);
        Log.d(TAG, "Next page rotation scheduled in " + (displayTime / 1000) + " seconds");
    }
    
    private void stopPageRotation() {
        if (pageHandler != null && pageRotationRunnable != null) {
            pageHandler.removeCallbacks(pageRotationRunnable);
        }
    }
    
    private void setupConfigurationUpdates() {
        configUpdateRunnable = () -> {
            Log.d(TAG, "Periodic configuration update");
            configManager.updateConfigFromGitHub(null);
            
            // Schedule next update
            configHandler.postDelayed(configUpdateRunnable, CONFIG_UPDATE_INTERVAL);
        };
        
        // Start periodic updates
        configHandler.postDelayed(configUpdateRunnable, CONFIG_UPDATE_INTERVAL);
    }
    
    private void startWatchdogService() {
        try {
            Intent watchdogIntent = new Intent(this, WatchdogService.class);
            startService(watchdogIntent);
            Log.i(TAG, "WatchdogService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WatchdogService", e);
        }
    }
    
    private void showWebView() {
        isErrorState = false;
        webView.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        
        // Cancel any pending retries
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
    }
    
    private void showError(String message) {
        isErrorState = true;
        webView.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
        retryButton.setVisibility(View.VISIBLE);
        
        Log.e(TAG, "Showing error: " + message);
        
        // Schedule automatic retry
        scheduleAutomaticRetry();
    }
    
    private void scheduleAutomaticRetry() {
        retryRunnable = () -> {
            Log.i(TAG, "Automatic retry triggered");
            retryCurrentPage();
        };
        
        retryHandler.postDelayed(retryRunnable, RETRY_INTERVAL);
        Log.d(TAG, "Automatic retry scheduled in " + (RETRY_INTERVAL / 1000) + " seconds");
    }
    
    private void retryCurrentPage() {
        if (pages != null && !pages.isEmpty()) {
            loadCurrentPage();
        } else {
            // Try to reload configuration
            loadConfiguration();
        }
    }
    
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    // ConfigUpdateListener implementation
    @Override
    public void onConfigUpdated(DeviceConfig config) {
        Log.i(TAG, "Configuration updated from GitHub");
        runOnUiThread(() -> {
            currentConfig = config;
            applyConfiguration(config);
            
            Toast.makeText(this, "Configuration updated", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConfigError(String error) {
        Log.w(TAG, "Configuration update error: " + error);
        runOnUiThread(() -> {
            // Don't show error if we're already in error state
            if (!isErrorState) {
                Toast.makeText(this, "Config update failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        
        // Resume WebView
        if (webView != null) {
            webView.onResume();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Pause WebView
        if (webView != null) {
            webView.onPause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Log.i(TAG, "MainActivity destroying");
        
        // Clean up handlers
        stopPageRotation();
        
        if (configHandler != null && configUpdateRunnable != null) {
            configHandler.removeCallbacks(configUpdateRunnable);
        }
        
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
        
        // Clean up configuration manager
        if (configManager != null) {
            configManager.shutdown();
        }
        
        // Clean up WebView
        if (webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
        }
        
        // Stop watchdog service
        try {
            Intent watchdogIntent = new Intent(this, WatchdogService.class);
            stopService(watchdogIntent);
            Log.i(TAG, "WatchdogService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop WatchdogService", e);
        }
    }
    
    @Override
    public void onBackPressed() {
        // Disable back button for kiosk mode
        // Uncomment the line below if you want to allow back navigation
        // super.onBackPressed();
    }
}
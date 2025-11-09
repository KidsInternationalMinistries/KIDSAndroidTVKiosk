package com.kidsim.tvkiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Map;

import com.kidsim.tvkiosk.config.ConfigurationManager;
import com.kidsim.tvkiosk.config.DeviceConfig;
import com.kidsim.tvkiosk.config.DeviceIdManager;
import com.kidsim.tvkiosk.config.GoogleSheetsConfigLoader;
import com.kidsim.tvkiosk.config.PageConfig;
import com.kidsim.tvkiosk.service.WatchdogService;
import com.kidsim.tvkiosk.utils.ErrorHandler;
import com.kidsim.tvkiosk.constants.AppConstants;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements ConfigurationManager.ConfigUpdateListener {
    
    private static final String TAG = "MainActivity";
    // Removed MAX_PAGES - now dynamic based on configuration
    
    // UI components - dynamic WebView arrays
    private WebView[] webViews;
    private WebView[] backupWebViews;
    private FrameLayout webViewContainer;
    private LinearLayout loadingLayout;
    private LinearLayout errorLayout;
    private TextView loadingProgress;
    private TextView errorText;
    private Button retryButton;
    private Button updateButton;
    
    // Configuration and timing
    private ConfigurationManager configManager;
    private DeviceIdManager deviceIdManager;
    private DeviceConfig currentConfig;
    private List<PageConfig> pages;
    private int currentPageIndex = 0;
    
    // Handlers for timing
    private Handler pageHandler;
    private Handler retryHandler;
    private Handler refreshHandler;
    private Handler configRefreshHandler; // For config refresh timer
    
    // Background tasks
    private ExecutorService executor;
    
    // Runnables
    private Runnable pageRotationRunnable;
    private Runnable retryRunnable;
    private Runnable refreshRunnable;
    private Runnable configRefreshRunnable; // For config refresh
    
    // State tracking
    private boolean isErrorState = false;
    private static final long RETRY_INTERVAL = AppConstants.RETRY_INTERVAL_MS;
    
    // WebView pool management
    private boolean[] pageLoadStates;
    private boolean[] backupPageLoadStates;
    private int pagesLoaded = 0;
    private boolean initialLoadComplete = false;
    private boolean isRefreshing = false;
    private int refreshPageIndex = 0;
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = AppConstants.REFRESH_INTERVAL_MS;
    
    // Connectivity
    private boolean isNetworkAvailable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ErrorHandler.logActivityStart(TAG, "MainActivity");
        
        // Initialize handlers
        pageHandler = new Handler(Looper.getMainLooper());
        retryHandler = new Handler(Looper.getMainLooper());
        refreshHandler = new Handler(Looper.getMainLooper());
        configRefreshHandler = new Handler(Looper.getMainLooper());
        
        // Initialize background executor
        executor = Executors.newSingleThreadExecutor();
        
        // WebView arrays will be initialized dynamically based on page count
        // pageLoadStates and backupPageLoadStates will be created in setupWebViewsForPages()
        
        // Setup kiosk mode
        setupKioskMode();
        
        setContentView(R.layout.activity_main);
        
        // Initialize UI components
        initializeViews();
        
        // Initialize configuration manager
        configManager = new ConfigurationManager(this);
        configManager.setConfigUpdateListener(this);
        
        // Initialize device ID manager
        deviceIdManager = new DeviceIdManager(this);
        
        // Check if device ID is configured, show setup if needed
        // Configuration loading will happen after setup is complete
        checkDeviceIdConfiguration();
        
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
        // Get the WebView container for dynamic WebView creation
        webViewContainer = findViewById(R.id.webViewContainer);
        
        // WebView arrays will be created dynamically based on page count
        // Remove fixed WebView initialization - they'll be created programmatically
        
        // Initialize loading UI
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        // Initialize error UI
        errorLayout = findViewById(R.id.errorLayout);
        errorText = findViewById(R.id.errorText);
        retryButton = findViewById(R.id.retryButton);
        updateButton = findViewById(R.id.updateButton);
        
        // Setup WebView pool will be called dynamically when configuration is loaded
        setupErrorHandling();
        setupUpdateButton();
    }
    
    private void setupWebViewsForPages(int pageCount) {
        Log.i(TAG, "Setting up " + pageCount + " WebViews dynamically");
        
        // Get device dimensions upfront
        int deviceWidth = getResources().getDisplayMetrics().widthPixels;
        int deviceHeight = getResources().getDisplayMetrics().heightPixels;
        Log.d(TAG, "Device dimensions: " + deviceWidth + "x" + deviceHeight);
        
        // Clear any existing WebViews from the container
        webViewContainer.removeAllViews();
        
        // Create arrays based on actual page count
        webViews = new WebView[pageCount];
        backupWebViews = new WebView[pageCount];
        pageLoadStates = new boolean[pageCount];
        backupPageLoadStates = new boolean[pageCount];
        
        // Create WebViews programmatically
        for (int i = 0; i < pageCount; i++) {
            // Create main WebView with rotation applied immediately
            webViews[i] = createRotatedWebView("Main-" + i, deviceWidth, deviceHeight);
            webViewContainer.addView(webViews[i]);
            
            // Create backup WebView with rotation applied immediately
            backupWebViews[i] = createRotatedWebView("Backup-" + i, deviceWidth, deviceHeight);
            webViewContainer.addView(backupWebViews[i]);
            
            // Initialize load states
            pageLoadStates[i] = false;
            backupPageLoadStates[i] = false;
            
            Log.d(TAG, "Created rotated WebView pair " + i + " for page " + i);
        }
        
        // Setup refresh monitoring
        setupRefreshMonitoring();
    }
    
    private WebView createRotatedWebView(String tag, int deviceWidth, int deviceHeight) {
        // Create WebView
        WebView webView = new WebView(this);
        
        // Read orientation from SharedPreferences (local device setting), not from Google Sheets config
        SharedPreferences preferences = getSharedPreferences("KioskUpdatePrefs", MODE_PRIVATE);
        String localOrientation = preferences.getString("orientation", "Landscape");
        boolean shouldRotate = "Portrait".equalsIgnoreCase(localOrientation);
        
        Log.d(TAG, "Creating WebView " + tag + " - Local orientation setting: '" + localOrientation + "' (shouldRotate=" + shouldRotate + ")");
        Log.d(TAG, "Orientation comparison: localOrientation='" + localOrientation + "', Portrait check=" + "Portrait".equalsIgnoreCase(localOrientation));
        Log.d(TAG, "Device dimensions: " + deviceWidth + "x" + deviceHeight);
        
        FrameLayout.LayoutParams layoutParams;
        
        if (!shouldRotate) {
            // Setting is Landscape - no rotation needed
            layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            webView.setLayoutParams(layoutParams);
            
            // Ensure no rotation is applied
            webView.setRotation(0f);
            webView.setScaleX(1.0f);
            webView.setScaleY(1.0f);
            
            Log.d(TAG, "Created landscape WebView " + tag + " with MATCH_PARENT dimensions (no rotation)");
        } else {
            // Setting is Portrait - apply rotation for landscape content
            layoutParams = new FrameLayout.LayoutParams(
                deviceHeight,  // Use device height as width (for landscape content on portrait setting)
                deviceWidth    // Use device width as height (for landscape content on portrait setting)
            );
            layoutParams.gravity = android.view.Gravity.CENTER;
            webView.setLayoutParams(layoutParams);
            
            // Apply rotation
            webView.setRotation(90f);
            webView.setPivotX(deviceHeight / 2f);
            webView.setPivotY(deviceWidth / 2f);
            
            Log.d(TAG, "Created rotated WebView " + tag + " with dimensions " + deviceHeight + "x" + deviceWidth + " (rotated from " + deviceWidth + "x" + deviceHeight + " for portrait setting)");
            Log.d(TAG, "Rotation details: rotation=90f, pivotX=" + (deviceHeight / 2f) + ", pivotY=" + (deviceWidth / 2f));
        }
        
        // Set properties
        webView.setVisibility(View.GONE);
        webView.setBackgroundColor(android.graphics.Color.BLACK);
        
        // Setup WebView instance
        setupWebViewInstance(webView, tag);
        
        return webView;
    }
    
    private void setupWebViewPool() {
        // Configure all WebViews with the same settings
        for (int i = 0; i < 3; i++) {
            setupWebViewInstance(webViews[i], "Main-" + i);
            setupWebViewInstance(backupWebViews[i], "Backup-" + i);
            
            // Initially hide all WebViews
            webViews[i].setVisibility(View.GONE);
            backupWebViews[i].setVisibility(View.GONE);
        }
        
        // Setup refresh monitoring
        setupRefreshMonitoring();
    }
    
    private void setupWebViewInstance(WebView webView, String tag) {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);
        
        // Enable DOM storage
        webSettings.setDomStorageEnabled(true);
        
        // Smart cache mode based on connectivity
        updateWebViewCacheMode(webSettings);
        
        // Allow file access
        webSettings.setAllowFileAccess(true);
        
        // Set user agent for better compatibility
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " AndroidTVKiosk/3.0-Pool-" + tag);
        
        // Scale to fit screen
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setSupportZoom(false);
        
        // Set WebView client to handle page loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebView " + tag + " loaded: " + url);
                
                // Mark this page as loaded in the pool
                markPageLoaded(view);
                
                // Hide system UI
                hideSystemUI();
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView " + tag + " error: " + description + " for URL: " + failingUrl);
                
                // Mark this page as failed
                markPageFailed(view);
                
                // Show error if this is the currently visible page
                if (view.getVisibility() == View.VISIBLE) {
                    showError("Failed to load page: " + description);
                }
            }
        });
    }
    
    private void setupRefreshMonitoring() {
        // Start background refresh system
        refreshHandler.postDelayed(this::checkForRefresh, REFRESH_INTERVAL);
    }
    
    private void updateWebViewCacheMode(WebSettings webSettings) {
        if (isNetworkConnected()) {
            // Network available - use cache but allow fresh loads
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            Log.d(TAG, "Network available: Using LOAD_DEFAULT cache mode");
        } else {
            // Network unavailable - prefer cache
            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            Log.d(TAG, "Network unavailable: Using LOAD_CACHE_ELSE_NETWORK cache mode");
        }
    }
    
    private void markPageLoaded(WebView webView) {
        // Find which WebView this is and mark as loaded
        for (int i = 0; i < pages.size(); i++) {
            if (webViews[i] == webView) {
                pageLoadStates[i] = true;
                pagesLoaded++;
                Log.d(TAG, "Main WebView " + i + " loaded. Total loaded: " + pagesLoaded);
                
                // Update loading progress
                updateLoadingProgress();
                break;
            } else if (backupWebViews[i] == webView) {
                backupPageLoadStates[i] = true;
                Log.d(TAG, "Backup WebView " + i + " loaded");
                break;
            }
        }
        
        // Check if initial load is complete - wait for ALL pages to load
        if (!initialLoadComplete && pagesLoaded >= pages.size()) {
            initialLoadComplete = true;
            showFirstPage();
            Log.i(TAG, "All " + pages.size() + " pages loaded - showing first page");
        }
    }
    
    private void markPageFailed(WebView webView) {
        // Find which WebView this is and mark as failed
        for (int i = 0; i < pages.size(); i++) {
            if (webViews[i] == webView) {
                pageLoadStates[i] = false;
                Log.w(TAG, "Main WebView " + i + " failed to load");
                break;
            } else if (backupWebViews[i] == webView) {
                backupPageLoadStates[i] = false;
                Log.w(TAG, "Backup WebView " + i + " failed to load");
                break;
            }
        }
    }
    
    private void showFirstPage() {
        // All pages should be loaded by now, show page 0 and start rotation
        if (pages.size() > 0) {
            currentPageIndex = 0;
            showPage(0);
            hideErrorState();
            hideLoadingState();
            
            // Start the page rotation timer
            setupPageRotationTimer();
            
            Log.i(TAG, "All pages loaded - showing page 0 and starting rotation");
        } else {
            showError("No pages available to display");
        }
    }
    
    private void showPage(int pageIndex) {
        // Hide all pages first
        for (int i = 0; i < pages.size(); i++) {
            webViews[i].setVisibility(View.GONE);
            backupWebViews[i].setVisibility(View.GONE);
        }
        
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            Log.w(TAG, "Invalid page index: " + pageIndex);
            return;
        }
        
        // Show the requested page directly - each page has its own WebView
        webViews[pageIndex].setVisibility(View.VISIBLE);
        currentPageIndex = pageIndex;
        
        Log.d(TAG, "Showing page: " + pageIndex + " in dedicated WebView " + pageIndex);
    }
    
    private void checkForRefresh() {
        if (isRefreshing || !isNetworkConnected()) {
            // Skip refresh if already refreshing or no network
            refreshHandler.postDelayed(this::checkForRefresh, REFRESH_INTERVAL);
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime >= REFRESH_INTERVAL) {
            startBackgroundRefresh();
        }
        
        // Schedule next check
        refreshHandler.postDelayed(this::checkForRefresh, REFRESH_INTERVAL);
    }
    
    private void startBackgroundRefresh() {
        if (pages == null || pages.size() == 0) {
            Log.w(TAG, "No pages to refresh");
            return;
        }
        
        isRefreshing = true;
        refreshPageIndex = 0;
        lastRefreshTime = System.currentTimeMillis();
        
        Log.i(TAG, "Starting background refresh of all pages");
        refreshNextPage();
    }
    
    private void refreshNextPage() {
        if (refreshPageIndex >= pages.size() || refreshPageIndex >= 3) {
            // Refresh complete
            isRefreshing = false;
            Log.i(TAG, "Background refresh completed");
            
            // Swap backup WebViews with main WebViews if refresh was successful
            swapToRefreshedPages();
            return;
        }
        
        PageConfig page = pages.get(refreshPageIndex);
        String pageUrl = page.getUrl();
        WebView backupWebView = backupWebViews[refreshPageIndex];
        
        Log.d(TAG, "Refreshing page " + refreshPageIndex + ": " + pageUrl);
        
        // Load the page in the backup WebView
        backupWebView.loadUrl(pageUrl);
        
        // Move to next page after a delay
        refreshHandler.postDelayed(() -> {
            refreshPageIndex++;
            refreshNextPage();
        }, AppConstants.PAGE_ROTATION_DELAY_MS); // 5 second delay between page refreshes
    }
    
    private void swapToRefreshedPages() {
        // Only swap pages that loaded successfully in backup WebViews
        for (int i = 0; i < 3; i++) {
            if (backupPageLoadStates[i]) {
                // Hide current main WebView
                webViews[i].setVisibility(View.GONE);
                
                // Swap the WebView references
                WebView temp = webViews[i];
                webViews[i] = backupWebViews[i];
                backupWebViews[i] = temp;
                
                // Update state
                pageLoadStates[i] = true;
                backupPageLoadStates[i] = false;
                
                // Show the refreshed page if it's the current one
                if (i == currentPageIndex) {
                    webViews[i].setVisibility(View.VISIBLE);
                }
                
                Log.i(TAG, "Swapped to refreshed page: " + i);
            }
        }
    }
    
    private void loadPagesIntoPool() {
        // Reset states
        pagesLoaded = 0;
        initialLoadComplete = false;
        
        // Initialize all load states to false
        for (int i = 0; i < pages.size(); i++) {
            pageLoadStates[i] = false;
            backupPageLoadStates[i] = false;
        }
        
        // Show loading state initially
        showLoadingState();
        
        // Load ALL pages into their corresponding WebViews
        for (int i = 0; i < pages.size(); i++) {
            loadPageIntoWebView(i);
        }
        
        // Don't show any pages yet - wait for all to load
        // The first page will be shown in onPageFinished when all pages are loaded
        Log.i(TAG, "Loading " + pages.size() + " pages into " + pages.size() + " WebViews, will show page 0 when all are loaded");
    }
    
    private void loadPageIntoWebView(int webViewIndex) {
        if (webViewIndex >= pages.size()) {
            return;
        }
        
        PageConfig page = pages.get(webViewIndex);
        String url = page.getUrl();
        
        // Add cache-busting parameter if device-level clearCache is enabled
        if (currentConfig != null && currentConfig.isClearCache()) {
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + "_t=" + System.currentTimeMillis();
            Log.d(TAG, "Cache clearing enabled for WebView " + webViewIndex);
        }
        
        Log.d(TAG, "Loading page " + webViewIndex + ": " + url);
        webViews[webViewIndex].loadUrl(url);
    }
    
    private void startPageRotationTimer() {
        if (pages == null || pages.isEmpty() || pages.size() <= 1) {
            Log.d(TAG, "No page rotation needed - single page or empty");
            return;
        }
        
        // Stop any existing rotation
        stopPageRotation();
        
        // Setup rotation timer
        setupPageRotationTimer();
        
        Log.i(TAG, "Started page rotation timer for " + pages.size() + " pages");
    }
    
    private void setupPageRotationTimer() {
        // Stop any existing timer first to prevent overlapping timers
        if (pageHandler != null && pageRotationRunnable != null) {
            pageHandler.removeCallbacks(pageRotationRunnable);
        }
        
        // Use the current page's display time for timing
        PageConfig currentPage = pages.get(currentPageIndex);
        long displayTime = currentPage.getDisplayTimeSeconds() * AppConstants.SECONDS_TO_MILLISECONDS;
        
        pageRotationRunnable = () -> {
            // Move to next page - cycle through ALL pages, not limited by WebView count
            int nextPageIndex = (currentPageIndex + 1) % pages.size();
            
            // Always switch to next page - loading state doesn't matter
            showPage(nextPageIndex);
            currentPageIndex = nextPageIndex;
            
            Log.d(TAG, "Rotated to page " + nextPageIndex + " of " + pages.size() + " total pages");
            
            // Schedule next rotation
            setupPageRotationTimer();
        };
        
        pageHandler.postDelayed(pageRotationRunnable, displayTime);
        Log.d(TAG, "Next page rotation scheduled in " + (displayTime / 1000) + " seconds");
    }
    


    
    private boolean isNetworkConnected() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            
            if (connected != isNetworkAvailable) {
                isNetworkAvailable = connected;
                Log.i(TAG, "Network connectivity changed: " + (connected ? "CONNECTED" : "DISCONNECTED"));
                
                if (connected) {
                    // Network restored
                    Log.i(TAG, "Network restored, will attempt fresh content loading");
                } else {
                    Log.w(TAG, "Network lost, will use cached content");
                }
            }
            
            return connected;
        } catch (Exception e) {
            ErrorHandler.logError(TAG, "Error checking network connectivity", e);
            return false;
        }
    }
    

    
    private void setupErrorHandling() {
        retryButton.setOnClickListener(v -> {
            ErrorHandler.logUserAction(TAG, "Retry button");
            retryCurrentPage();
        });
    }
    
    private void setupUpdateButton() {
        updateButton.setOnClickListener(v -> {
            ErrorHandler.logUserAction(TAG, "Update button");
            openUpdateDownloader();
        });
        
        // Show update button only for test devices
        configManager = new ConfigurationManager(this);
        boolean isTestDevice = isTestDevice();
        updateButton.setVisibility(isTestDevice ? View.VISIBLE : View.GONE);
        
        Log.i(TAG, "Update button " + (isTestDevice ? "visible" : "hidden") + " for this device");
    }
    
    private boolean isTestDevice() {
        try {
            String deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            return "test".equals(deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get device ID for update button", e);
            return false;
        }
    }
    
    private void openUpdateDownloader() {
        try {
            // GitHub release URL for test APK download
            String testApkUrl = AppConstants.GitHub.TEST_APK_URL;
            
            // Try to open with Downloader app (common on Android TV)
            Intent downloaderIntent = new Intent(Intent.ACTION_VIEW);
            downloaderIntent.setData(android.net.Uri.parse(testApkUrl));
            downloaderIntent.setPackage("com.esaba.downloader");
            
            if (downloaderIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(downloaderIntent);
                Log.i(TAG, "Opened test APK URL in Downloader app");
            } else {
                // Fallback to browser
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(testApkUrl));
                startActivity(browserIntent);
                Log.i(TAG, "Opened test APK URL in browser");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to open update downloader", e);
            Toast.makeText(this, "Failed to open downloader: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void loadConfiguration() {
        String deviceId = deviceIdManager.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            String message = "No device ID configured. Please configure device ID first.";
            Log.i(TAG, message);
            showError(message);
            return;
        }
        
        // Always load fresh configuration from Google Sheets
        Log.i(TAG, "Loading configuration from Google Sheets for device: " + deviceId);
        configManager.loadConfigurationFromGoogleSheets(deviceId);
        
        // Note: Configuration will be applied via onConfigurationUpdated callback
        // or error will be shown via onConfigurationError callback
    }
    
    private void applyConfiguration(DeviceConfig config) {
        Log.i(TAG, "Applying configuration for device: " + config.getDeviceName());
        
        // Setup pages
        pages = config.getPages();
        if (pages == null || pages.isEmpty()) {
            Log.w(TAG, "No pages configured, using default");
            showError("No pages configured");
            return;
        }
        
        // Setup WebViews dynamically based on page count
        setupWebViewsForPages(pages.size());
        
        // Apply orientation AFTER WebViews are created
        SharedPreferences preferences = getSharedPreferences("KioskUpdatePrefs", MODE_PRIVATE);
        
        // Debug: Show all SharedPreferences values
        Map<String, ?> allPrefs = preferences.getAll();
        Log.d(TAG, "All SharedPreferences values:");
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            Log.d(TAG, "  " + entry.getKey() + " = '" + entry.getValue() + "'");
        }
        
        String localOrientation = preferences.getString("orientation", "Landscape");
        Log.d(TAG, "Reading orientation key directly: '" + localOrientation + "'");
        
        // Note: Orientation already applied during WebView creation, no need to apply again
        Log.d(TAG, "Orientation '" + localOrientation + "' was applied during WebView creation");
        
        // Check network connectivity and update cache modes for all WebViews
        isNetworkConnected();
        for (int i = 0; i < pages.size(); i++) {
            if (webViews[i] != null) {
                updateWebViewCacheMode(webViews[i].getSettings());
            }
            if (backupWebViews[i] != null) {
                updateWebViewCacheMode(backupWebViews[i].getSettings());
            }
        }
        
        // Load pages into WebView pool
        loadPagesIntoPool();
        
        // Start page rotation timer
        startPageRotationTimer();
        
        // Start configuration refresh timer
        startConfigurationRefreshTimer();
        
        Log.i(TAG, "Configuration applied with " + pages.size() + " pages, network: " + 
              (isNetworkAvailable ? "CONNECTED" : "OFFLINE"));
    }
    
    private void startConfigurationRefreshTimer() {
        if (currentConfig == null) {
            return;
        }
        
        // Stop any existing config refresh timer
        if (configRefreshRunnable != null) {
            configRefreshHandler.removeCallbacks(configRefreshRunnable);
        }
        
        // Get refresh interval in minutes, convert to milliseconds
        long refreshIntervalMs = currentConfig.getRefreshIntervalMinutes() * 60 * 1000L;
        
        configRefreshRunnable = () -> {
            Log.i(TAG, "Attempting to refresh configuration from Google Sheets");
            String deviceId = deviceIdManager.getDeviceId();
            if (deviceId != null && !deviceId.isEmpty()) {
                // Try to refresh config - if it fails, we keep current config and try again later
                configManager.loadConfigurationFromGoogleSheets(deviceId);
            }
            
            // Schedule next refresh (will be rescheduled if config updates)
            configRefreshHandler.postDelayed(configRefreshRunnable, refreshIntervalMs);
        };
        
        // Schedule first refresh
        configRefreshHandler.postDelayed(configRefreshRunnable, refreshIntervalMs);
        Log.i(TAG, "Configuration refresh scheduled every " + currentConfig.getRefreshIntervalMinutes() + " minutes");
    }
    
    private void applyOrientation(String orientation) {
        // Don't set device orientation - let device stay in natural orientation
        // Instead, rotate WebView content if portrait is requested
        
        if ("portrait".equalsIgnoreCase(orientation)) {
            // Apply WebView rotation for portrait content
            applyWebViewRotation(true);
            Log.d(TAG, "Applied portrait rotation to WebViews, device stays in natural orientation");
        } else {
            // Default landscape - no rotation needed
            applyWebViewRotation(false);
            Log.d(TAG, "Applied landscape orientation to WebViews (no rotation)");
        }
    }
    
    private void applyWebViewRotation(boolean isPortrait) {
        if (webViews == null) {
            Log.d(TAG, "WebViews not initialized yet, orientation will be applied after setup");
            return;
        }
        
        Log.d(TAG, "Applying " + (isPortrait ? "portrait" : "landscape") + " orientation to all WebViews");
        
        for (int i = 0; i < webViews.length; i++) {
            if (webViews[i] != null) {
                applyRotationToWebView(webViews[i], isPortrait);
            }
            if (backupWebViews != null && i < backupWebViews.length && backupWebViews[i] != null) {
                applyRotationToWebView(backupWebViews[i], isPortrait);
            }
        }
        
        Log.d(TAG, "Orientation applied to all WebViews - they will maintain rotation when shown/hidden");
    }
    
    private void applyRotationToWebView(WebView webView, boolean isPortrait) {
        if (isPortrait) {
            // Use a post to ensure WebView is fully laid out
            webView.post(() -> {
                if (webView.getWidth() > 0 && webView.getHeight() > 0) {
                    applyPortraitRotationNow(webView);
                } else {
                    // Use ViewTreeObserver if dimensions aren't available yet
                    webView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (webView.getWidth() > 0 && webView.getHeight() > 0) {
                                webView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                applyPortraitRotationNow(webView);
                            }
                        }
                    });
                }
            });
        } else {
            // Reset to normal landscape orientation
            webView.setRotation(0f);
            webView.setScaleX(1.0f);
            webView.setScaleY(1.0f);
            
            // Reset layout params to match parent
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) webView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = android.view.Gravity.NO_GRAVITY;
            webView.setLayoutParams(layoutParams);
            
            Log.d(TAG, "Reset WebView to landscape orientation (no rotation)");
        }
    }
    
    private void applyPortraitRotationNow(WebView webView) {
        int width = webView.getWidth();
        int height = webView.getHeight();
        
        Log.d(TAG, "WebView dimensions: " + width + "x" + height + " before rotation");
        
        // For portrait rotation, we need to swap dimensions
        // The device is landscape (e.g., 2400x1080), but we want portrait content
        // So we need to fit portrait content (1080 width) into landscape space
        
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) webView.getLayoutParams();
        
        // Swap width and height for portrait content
        layoutParams.width = height;  // Use original height as new width
        layoutParams.height = width;  // Use original width as new height
        
        // Center the rotated WebView
        layoutParams.gravity = android.view.Gravity.CENTER;
        
        webView.setLayoutParams(layoutParams);
        
        // Apply rotation around center
        webView.setRotation(90f);
        webView.setPivotX(height / 2f);  // Pivot based on new dimensions
        webView.setPivotY(width / 2f);
        
        Log.d(TAG, "Applied 90-degree rotation to WebView for portrait content");
        Log.d(TAG, "Swapped dimensions from " + width + "x" + height + " to " + height + "x" + width);
    }
    
    private void stopPageRotation() {
        if (pageHandler != null && pageRotationRunnable != null) {
            pageHandler.removeCallbacks(pageRotationRunnable);
        }
    }
    
    private void setupConfigurationUpdates() {
        // Configuration updates are now handled only through UpdateActivity
        // No automatic GitHub configuration fetching since we use Google Sheets
        Log.d(TAG, "Configuration updates disabled - use UpdateActivity for manual updates");
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
    
    private void hideErrorState() {
        isErrorState = false;
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        
        // Cancel any pending retries
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
    }
    
    private void showError(String message) {
        isErrorState = true;
        
        // Hide loading state
        hideLoadingState();
        
        // Hide WebViews only if they exist
        if (webViews != null) {
            for (int i = 0; i < webViews.length; i++) {
                if (webViews[i] != null) {
                    webViews[i].setVisibility(View.GONE);
                }
                if (backupWebViews[i] != null) {
                    backupWebViews[i].setVisibility(View.GONE);
                }
            }
        }
        
        // Show error UI
        if (errorText != null) {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(message);
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
        }
        
        ErrorHandler.logError(TAG, "Showing error: " + message);
        
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
            // Reload pages into pool
            loadPagesIntoPool();
        } else {
            // Try to reload configuration
            loadConfiguration();
        }
    }
    
    // Loading UI management methods
    private void showLoadingState() {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.VISIBLE);
        }
        updateLoadingProgress();
    }
    
    private void hideLoadingState() {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }
    }
    
    private void updateLoadingProgress() {
        if (loadingProgress != null && pages != null) {
            // Show progress for all pages, not limited by WebView count
            String progressText = pagesLoaded + "/" + pages.size() + " pages loaded";
            loadingProgress.setText(progressText);
            Log.d(TAG, "Loading progress: " + progressText);
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
        
        // Resume all WebViews in the dynamic lists
        if (webViews != null) {
            for (WebView webView : webViews) {
                if (webView != null) {
                    webView.onResume();
                }
            }
        }
        
        if (backupWebViews != null) {
            for (WebView webView : backupWebViews) {
                if (webView != null) {
                    webView.onResume();
                }
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Pause all WebViews in the dynamic lists
        if (webViews != null) {
            for (WebView webView : webViews) {
                if (webView != null) {
                    webView.onPause();
                }
            }
        }
        
        if (backupWebViews != null) {
            for (WebView webView : backupWebViews) {
                if (webView != null) {
                    webView.onPause();
                }
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        ErrorHandler.logActivityEnd(TAG, "MainActivity");
        
        // Clean up handlers
        stopPageRotation();
        
        // Stop configuration refresh timer
        if (configRefreshRunnable != null) {
            configRefreshHandler.removeCallbacks(configRefreshRunnable);
        }
        
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
        
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        
        // Clean up configuration manager
        if (configManager != null) {
            configManager.shutdown();
        }
        
        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
        }
        
        // Clean up all WebViews in the pool
        for (int i = 0; i < 3; i++) {
            if (webViews[i] != null) {
                webViews[i].clearCache(true);
                webViews[i].clearHistory();
                webViews[i].destroy();
            }
            if (backupWebViews[i] != null) {
                backupWebViews[i].clearCache(true);
                backupWebViews[i].clearHistory();
                backupWebViews[i].destroy();
            }
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
    
    /**
     * Check device ID configuration and proceed with kiosk loading
     * MainActivity now shows "No configuration" if not configured via UpdateActivity
     */
    private void checkDeviceIdConfiguration() {
        if (!deviceIdManager.isDeviceIdConfigured()) {
            Log.i(TAG, "Device ID not configured, will show no configuration message");
        } else {
            Log.i(TAG, "Device ID configured: " + deviceIdManager.getDeviceId());
        }
        
        // Always try to load configuration - will show appropriate message if none found
        loadConfiguration();
    }
    
    /**
     * Show device ID setup dialog for first-time configuration
     */
    private void showDeviceIdSetupDialog() {
        // Create a custom layout with two spinners
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        // Orientation selection
        TextView orientationLabel = new TextView(this);
        orientationLabel.setText("Select Orientation:");
        orientationLabel.setTextSize(16);
        layout.addView(orientationLabel);
        
        Spinner orientationSpinner = new Spinner(this);
        List<String> orientations = new ArrayList<>();
        orientations.add("Landscape");
        orientations.add("Portrait");
        setupSpinner(orientationSpinner, orientations, false);
        layout.addView(orientationSpinner);
        
        // Add some spacing
        TextView spacer = new TextView(this);
        spacer.setText("");
        spacer.setPadding(0, 30, 0, 10);
        layout.addView(spacer);
        
        // Device ID selection
        TextView deviceIdLabel = new TextView(this);
        deviceIdLabel.setText("Select Device ID:");
        deviceIdLabel.setTextSize(16);
        layout.addView(deviceIdLabel);
        
        Spinner deviceIdSpinner = new Spinner(this);
        // Load device IDs from Google Sheets
        loadDeviceIdsForSetup(deviceIdSpinner);
        layout.addView(deviceIdSpinner);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("First Time Setup")
            .setMessage("Please configure this device:")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Apply Configuration", null) // Set to null initially
            .setNegativeButton("Cancel", (d, which) -> {
                // Close app if user cancels setup
                finish();
            })
            .create();
            
        dialog.setOnShowListener(d -> {
            Button applyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            applyButton.setEnabled(false); // Initially disabled
            
            // Enable button only when both spinners have selections
            Runnable checkSelections = () -> {
                boolean orientationSelected = orientationSpinner.getSelectedItem() != null;
                boolean deviceIdSelected = deviceIdSpinner.getSelectedItem() != null && 
                    !deviceIdSpinner.getSelectedItem().toString().startsWith("Loading");
                applyButton.setEnabled(orientationSelected && deviceIdSelected);
            };
            
            // Set up listeners to check selections
            orientationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    checkSelections.run();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    checkSelections.run();
                }
            });
            
            deviceIdSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    checkSelections.run();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    checkSelections.run();
                }
            });
            
            applyButton.setOnClickListener(v -> {
                String selectedOrientation = (String) orientationSpinner.getSelectedItem();
                String selectedDeviceId = (String) deviceIdSpinner.getSelectedItem();
                
                if (selectedOrientation != null && selectedDeviceId != null && 
                    !selectedDeviceId.startsWith("Loading")) {
                    
                    // Store both orientation and device ID
                    deviceIdManager.setDeviceId(selectedDeviceId);
                    // Store orientation in SharedPreferences for later use
                    getSharedPreferences("device_config", MODE_PRIVATE)
                        .edit()
                        .putString("orientation", selectedOrientation.toLowerCase())
                        .apply();
                    
                    Toast.makeText(this, "Configuration applied: " + selectedOrientation + " / " + selectedDeviceId, 
                        Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Device configuration applied: " + selectedOrientation + " / " + selectedDeviceId);
                    
                    dialog.dismiss();
                    
                    // Now load configuration with new settings
                    loadConfiguration();
                } else {
                    Toast.makeText(this, "Please select both orientation and device ID", 
                        Toast.LENGTH_SHORT).show();
                }
            });
        });
        
        dialog.show();
    }
    
    /**
     * Load available device IDs from Google Sheets for setup dialog
     */
    private void loadDeviceIdsForSetup(Spinner spinner) {
        // Use Google Sheets API to load device IDs
        ConfigurationManager configManager = new ConfigurationManager(this);
        GoogleSheetsConfigLoader sheetsLoader = new GoogleSheetsConfigLoader(
            configManager.getGoogleSheetsId(), 
            configManager.getGoogleSheetsApiKey()
        );
        
        // Set loading message while fetching device IDs
        List<String> loadingList = new ArrayList<>();
        loadingList.add("Loading device IDs...");
        setupSpinner(spinner, loadingList, false);
        
        // Fetch device IDs in background using Google Sheets API
        sheetsLoader.loadAvailableDeviceIds(new GoogleSheetsConfigLoader.DeviceListListener() {
            @Override
            public void onDeviceListLoaded(List<String> deviceIds) {
                runOnUiThread(() -> {
                    setupSpinner(spinner, deviceIds, false);
                });
            }
            
            @Override
            public void onDeviceListFailed(String error) {
                Log.e(TAG, "Failed to load device IDs for setup: " + error);
                runOnUiThread(() -> {
                    // Use fallback device IDs based on the test
                    List<String> fallbackIds = new ArrayList<>();
                    fallbackIds.add("Test1");
                    fallbackIds.add("Test2");
                    
                    setupSpinner(spinner, fallbackIds, false);
                    
                    Toast.makeText(MainActivity.this, "Using fallback device IDs", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Utility method to setup spinner with consistent styling
     */
    private void setupSpinner(Spinner spinner, List<String> items, boolean useCustomLayout) {
        ArrayAdapter<String> adapter;
        if (useCustomLayout) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        } else {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
        spinner.setAdapter(adapter);
    }
    
}
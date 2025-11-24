package com.kidsim.tvkiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import com.kidsim.tvkiosk.config.DeviceConfig;
import com.kidsim.tvkiosk.config.DeviceIdManager;
import com.kidsim.tvkiosk.config.GoogleSheetsConfigLoader;
import com.kidsim.tvkiosk.config.PageConfig;
import com.kidsim.tvkiosk.service.WatchdogService;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements ConfigurationManager.ConfigUpdateListener {
    
    private static final String TAG = "MainActivity";
    
    // UI components
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
    private Handler configHandler;
    private Handler retryHandler;
    private Handler refreshHandler;
    
    // Background tasks
    private ExecutorService executor;
    
    // Runnables
    private Runnable pageRotationRunnable;
    private Runnable configUpdateRunnable;
    private Runnable retryRunnable;
    private Runnable refreshRunnable;
    
    // State tracking
    private boolean isErrorState = false;
    private long lastConfigUpdate = 0;
    private static final long CONFIG_UPDATE_INTERVAL = 3600000; // 1 hour
    private static final long RETRY_INTERVAL = 300000; // 5 minutes
    
    // Double back press tracking for configuration access
    private long lastBackPressTime = 0;
    private static final long DOUBLE_BACK_PRESS_INTERVAL = 2000; // 2 seconds
    
    // WebView pool management
    private boolean[] pageLoadStates;
    private boolean[] backupPageLoadStates;
    private int pagesLoaded = 0;
    private boolean initialLoadComplete = false;
    private boolean isRefreshing = false;
    private int refreshPageIndex = 0;
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 10 * 60 * 1000; // 10 minutes
    
    // Connectivity
    private boolean isNetworkAvailable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "MainActivity starting");
        
        // Initialize handlers
        pageHandler = new Handler(Looper.getMainLooper());
        configHandler = new Handler(Looper.getMainLooper());
        retryHandler = new Handler(Looper.getMainLooper());
        refreshHandler = new Handler(Looper.getMainLooper());
        
        // Initialize background executor
        executor = Executors.newSingleThreadExecutor();
        
        // WebView arrays will be initialized dynamically based on page count
        
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
        
        // Check if we're returning from configuration screen
        boolean returnFromConfiguration = getIntent().getBooleanExtra("returnFromConfiguration", false);
        
        if (returnFromConfiguration) {
            Log.i(TAG, "Returning from configuration screen, loading app directly");
            // Load configuration directly without checking device ID
            loadConfiguration();
        } else {
            // Check if device ID is configured, show setup if needed
            // Configuration loading will happen after setup is complete
            checkDeviceIdConfiguration();
        }
        
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
        // Initialize WebView container
        webViewContainer = findViewById(R.id.webViewContainer);
        
        // Initialize loading UI
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        // Initialize error UI
        errorLayout = findViewById(R.id.errorLayout);
        errorText = findViewById(R.id.errorText);
        retryButton = findViewById(R.id.retryButton);
        updateButton = findViewById(R.id.updateButton);
        
        // WebViews will be created dynamically based on the number of pages
        // This happens in setupDynamicWebViews() which is called after configuration is loaded
        
        setupErrorHandling();
        setupUpdateButton();
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
        // Stop any existing refresh monitoring first
        if (refreshHandler != null) {
            refreshHandler.removeCallbacks(this::checkForRefresh);
        }
        
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
        int webViewCount = webViews != null ? webViews.length : 0;
        for (int i = 0; i < webViewCount; i++) {
            if (webViews[i] == webView) {
                pageLoadStates[i] = true;
                pagesLoaded++;
                Log.d(TAG, "Main WebView " + i + " loaded. Total loaded: " + pagesLoaded + "/" + webViewCount);
                
                // Update loading progress
                updateLoadingProgress();
                break;
            } else if (backupWebViews[i] == webView) {
                backupPageLoadStates[i] = true;
                Log.d(TAG, "Backup WebView " + i + " loaded");
                break;
            }
        }
        
        // Check if ALL pages are loaded (not just 1)
        if (!initialLoadComplete && pagesLoaded >= webViewCount) {
            initialLoadComplete = true;
            Log.i(TAG, "All " + webViewCount + " pages loaded! Starting page rotation.");
            showFirstPage();
            // Start page rotation timer now that all pages are loaded
            startPageRotationTimer();
        }
    }
    
    private void markPageFailed(WebView webView) {
        // Find which WebView this is and mark as failed
        int webViewCount = webViews != null ? webViews.length : 0;
        for (int i = 0; i < webViewCount; i++) {
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
        // Show the first successfully loaded page
        int webViewCount = webViews != null ? webViews.length : 0;
        for (int i = 0; i < webViewCount; i++) {
            if (pageLoadStates[i]) {
                currentPageIndex = i;
                showPage(i);
                hideErrorState();
                hideLoadingState();  // Hide loading UI when first page is ready
                Log.i(TAG, "Showing first loaded page: " + i);
                return;
            }
        }
        
        // If no pages loaded successfully, show error
        showError("No pages could be loaded");
    }
    
    private void showPage(int pageIndex) {
        // Safety check
        if (webViews == null || backupWebViews == null) {
            Log.w(TAG, "WebViews not initialized yet, cannot show page " + pageIndex);
            return;
        }
        
        // Hide all pages first
        int webViewCount = webViews.length;
        for (int i = 0; i < webViewCount; i++) {
            if (webViews[i] != null) {
                webViews[i].setVisibility(View.GONE);
            }
            if (backupWebViews[i] != null) {
                backupWebViews[i].setVisibility(View.GONE);
            }
        }
        
        // Show the requested page
        if (pageIndex >= 0 && pageIndex < webViewCount && webViews[pageIndex] != null) {
            webViews[pageIndex].setVisibility(View.VISIBLE);
            currentPageIndex = pageIndex;
            Log.d(TAG, "Showing page: " + pageIndex);
        } else {
            Log.w(TAG, "Invalid page index or WebView not ready: " + pageIndex);
        }
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
        
        // Schedule next check (only one at a time)
        refreshHandler.removeCallbacks(this::checkForRefresh);
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
        }, 5000); // 5 second delay between page refreshes
    }
    
    private void swapToRefreshedPages() {
        // Safety check
        if (webViews == null || backupWebViews == null || backupPageLoadStates == null) {
            Log.w(TAG, "WebViews not initialized, cannot swap pages");
            return;
        }
        
        // Only swap pages that loaded successfully in backup WebViews
        int webViewCount = webViews.length;
        for (int i = 0; i < webViewCount; i++) {
            if (backupPageLoadStates[i]) {
                // Hide current main WebView
                if (webViews[i] != null) {
                    webViews[i].setVisibility(View.GONE);
                }
                
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
        
        int webViewCount = webViews != null ? webViews.length : 0;
        
        // Reset all page load states
        for (int i = 0; i < webViewCount; i++) {
            pageLoadStates[i] = false;
            backupPageLoadStates[i] = false;
        }
        
        // Show loading state
        showLoadingState();
        
        // Load all pages into the WebView pool (no longer limited to 3)
        int pagesToLoad = Math.min(pages.size(), webViewCount);
        for (int i = 0; i < pagesToLoad; i++) {
            loadPageIntoWebView(i);
        }
        
        Log.i(TAG, "Loading " + pagesToLoad + " pages into WebView pool");
    }
    
    private void loadPageIntoWebView(int webViewIndex) {
        if (webViewIndex >= 3 || webViewIndex >= pages.size()) {
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
        // First stop any existing timer to prevent multiple concurrent timers
        if (pageHandler != null && pageRotationRunnable != null) {
            pageHandler.removeCallbacks(pageRotationRunnable);
        }
        
        // Use the first page's display time for timing
        PageConfig currentPage = pages.get(currentPageIndex);
        long displayTime = currentPage.getDisplayTimeSeconds() * 1000L;
        
        pageRotationRunnable = () -> {
            // Move to next page
            int nextPageIndex = (currentPageIndex + 1) % pages.size();
            
            // Switch to next page if it's loaded
            if (pageLoadStates != null && nextPageIndex < pageLoadStates.length && pageLoadStates[nextPageIndex]) {
                showPage(nextPageIndex);
                currentPageIndex = nextPageIndex;
                
                // Schedule next rotation
                setupPageRotationTimer();
            } else {
                Log.w(TAG, "Next page " + nextPageIndex + " not ready, retrying in 5 seconds");
                pageHandler.postDelayed(this::setupPageRotationTimer, 5000);
            }
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
            Log.e(TAG, "Error checking network connectivity", e);
            return false;
        }
    }
    

    
    private void setupErrorHandling() {
        retryButton.setOnClickListener(v -> {
            Log.i(TAG, "Retry button clicked");
            retryCurrentPage();
        });
    }
    
    private void setupUpdateButton() {
        updateButton.setOnClickListener(v -> {
            Log.i(TAG, "Update button clicked");
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
            String testApkUrl = "https://github.com/KidsInternationalMinistries/KIDSAndroidTVKiosk/releases/latest/download/app-test.apk";
            
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
        
        // Setup dynamic WebViews based on page count
        int pageCount = pages.size();
        Log.i(TAG, "Setting up " + pageCount + " pages");
        setupDynamicWebViews(pageCount);
        
        // Check network connectivity and update cache modes for all WebViews
        isNetworkConnected();
        for (int i = 0; i < pageCount; i++) {
            if (webViews[i] != null) {
                updateWebViewCacheMode(webViews[i].getSettings());
            }
            if (backupWebViews[i] != null) {
                updateWebViewCacheMode(backupWebViews[i].getSettings());
            }
        }
        
        // Load pages into WebView pool
        loadPagesIntoPool();
        
        Log.i(TAG, "Configuration applied with " + pages.size() + " pages, network: " + 
              (isNetworkAvailable ? "CONNECTED" : "OFFLINE"));
    }
    
    /**
     * Setup dynamic WebViews based on the number of pages
     */
    private void setupDynamicWebViews(int pageCount) {
        Log.i(TAG, "Setting up " + pageCount + " dynamic WebViews");
        
        // Safety check - make sure webViewContainer is initialized
        if (webViewContainer == null) {
            Log.e(TAG, "webViewContainer is null! Views not properly initialized.");
            return;
        }
        
        // Clear existing WebViews
        webViewContainer.removeAllViews();
        
        // Get device dimensions for rotation calculations
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int deviceWidth = displayMetrics.widthPixels;
        int deviceHeight = displayMetrics.heightPixels;
        
        Log.d(TAG, "Device dimensions: " + deviceWidth + "x" + deviceHeight);
        
        // Initialize arrays with dynamic size
        webViews = new WebView[pageCount];
        backupWebViews = new WebView[pageCount];
        pageLoadStates = new boolean[pageCount];
        backupPageLoadStates = new boolean[pageCount];
        
        // Get current orientation setting
        String currentOrientation = "landscape"; // default
        if (currentConfig != null && currentConfig.getOrientation() != null) {
            currentOrientation = currentConfig.getOrientation();
        }
        boolean isPortraitMode = "portrait".equalsIgnoreCase(currentOrientation);
        
        // Create WebViews dynamically
        for (int i = 0; i < pageCount; i++) {
            try {
                // Create main WebView
                webViews[i] = new WebView(this);
                webViews[i].setId(View.generateViewId());
                
                // Only apply rotation in portrait mode to display landscape content
                if (isPortraitMode) {
                    applyWebViewRotation(webViews[i], deviceWidth, deviceHeight);
                } else {
                    // Landscape mode - normal layout
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    );
                    webViews[i].setLayoutParams(layoutParams);
                }
                
                webViews[i].setBackgroundColor(0xFF000000); // Black background
                webViews[i].setVisibility(View.GONE);
                webViewContainer.addView(webViews[i]);
                
                // Create backup WebView
                backupWebViews[i] = new WebView(this);
                backupWebViews[i].setId(View.generateViewId());
                
                // Only apply rotation in portrait mode to display landscape content
                if (isPortraitMode) {
                    applyWebViewRotation(backupWebViews[i], deviceWidth, deviceHeight);
                } else {
                    // Landscape mode - normal layout
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    );
                    backupWebViews[i].setLayoutParams(layoutParams);
                }
                
                backupWebViews[i].setBackgroundColor(0xFF000000); // Black background
                backupWebViews[i].setVisibility(View.GONE);
                webViewContainer.addView(backupWebViews[i]);
                
                // Setup WebView instances
                setupWebViewInstance(webViews[i], "Main-" + i);
                setupWebViewInstance(backupWebViews[i], "Backup-" + i);
                
                Log.d(TAG, "Created rotated WebView pair " + i);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating WebView " + i, e);
                return;
            }
        }
        
        // Reset counters
        pagesLoaded = 0;
        initialLoadComplete = false;
        
        Log.i(TAG, "Dynamic WebView setup complete for " + pageCount + " pages with rotation");
    }
    
    private void applyWebViewRotation(WebView webView, int deviceWidth, int deviceHeight) {
        // Rotate the WebView by 90 degrees for portrait mode
        // This allows landscape web content to display properly in portrait orientation
        webView.setRotation(90f);
        
        // Swap width and height for the rotated view
        // Since we're rotating 90 degrees, the view's width becomes the device height
        // and the view's height becomes the device width
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
            deviceHeight, // Width becomes device height
            deviceWidth   // Height becomes device width
        );
        
        // Center the rotated view within the container
        layoutParams.gravity = android.view.Gravity.CENTER;
        
        webView.setLayoutParams(layoutParams);
        
        // Set the pivot point for rotation to the center of the view
        webView.setPivotX(deviceHeight / 2f);
        webView.setPivotY(deviceWidth / 2f);
        
        Log.d(TAG, "Applied 90° rotation for portrait mode: " + deviceWidth + "x" + deviceHeight + " -> " + deviceHeight + "x" + deviceWidth);
    }
    
    private void applyOrientation(String orientation) {
        int orientationValue = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        
        if ("portrait".equalsIgnoreCase(orientation)) {
            orientationValue = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        
        // Force orientation and disable sensor-based auto-rotation
        setRequestedOrientation(orientationValue);
        Log.d(TAG, "Set forced orientation to: " + orientation + " (sensors ignored)");
    }
    
    private void stopPageRotation() {
        if (pageHandler != null && pageRotationRunnable != null) {
            pageHandler.removeCallbacks(pageRotationRunnable);
        }
    }
    
    private void setupConfigurationUpdates() {
        // Stop any existing config updates first
        if (configHandler != null && configUpdateRunnable != null) {
            configHandler.removeCallbacks(configUpdateRunnable);
        }
        
        configUpdateRunnable = () -> {
            Log.d(TAG, "Periodic configuration update");
            configManager.updateConfigFromGitHub(null);
            
            // Schedule next update (only one at a time)
            configHandler.removeCallbacks(configUpdateRunnable);
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
        
        // Hide loading and WebViews
        hideLoadingState();
        if (webViews != null) {
            for (int i = 0; i < webViews.length; i++) {
                if (webViews[i] != null) {
                    webViews[i].setVisibility(View.GONE);
                }
            }
        }
        if (backupWebViews != null) {
            for (int i = 0; i < backupWebViews.length; i++) {
                if (backupWebViews[i] != null) {
                    backupWebViews[i].setVisibility(View.GONE);
                }
            }
        }
        
        // Show error UI
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
            int totalPages = pages.size(); // Use actual page count, not limited to 3
            String progressText = pagesLoaded + "/" + totalPages + " pages loaded";
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
        
        // Resume all WebViews in the pool
        if (webViews != null) {
            for (int i = 0; i < webViews.length; i++) {
                if (webViews[i] != null) {
                    webViews[i].onResume();
                }
            }
        }
        if (backupWebViews != null) {
            for (int i = 0; i < backupWebViews.length; i++) {
                if (backupWebViews[i] != null) {
                    backupWebViews[i].onResume();
                }
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Pause all WebViews in the pool
        if (webViews != null) {
            for (int i = 0; i < webViews.length; i++) {
                if (webViews[i] != null) {
                    webViews[i].onPause();
                }
            }
        }
        if (backupWebViews != null) {
            for (int i = 0; i < backupWebViews.length; i++) {
                if (backupWebViews[i] != null) {
                    backupWebViews[i].onPause();
                }
            }
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
        if (webViews != null) {
            for (int i = 0; i < webViews.length; i++) {
                if (webViews[i] != null) {
                    webViews[i].clearCache(true);
                    webViews[i].clearHistory();
                    webViews[i].destroy();
                }
            }
        }
        
        if (backupWebViews != null) {
            for (int i = 0; i < backupWebViews.length; i++) {
                if (backupWebViews[i] != null) {
                    backupWebViews[i].clearCache(true);
                    backupWebViews[i].clearHistory();
                    backupWebViews[i].destroy();
                }
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
        long currentTime = System.currentTimeMillis();
        
        // Check if this is a double back press (within 2 seconds)
        if (currentTime - lastBackPressTime < DOUBLE_BACK_PRESS_INTERVAL) {
            Log.i(TAG, "Double back press detected, opening configuration screen");
            openConfigurationScreen();
        } else {
            Log.d(TAG, "Single back press detected, waiting for double press");
            // Show a brief toast to let user know about double press
            Toast.makeText(this, "Press back again to open configuration", Toast.LENGTH_SHORT).show();
        }
        
        // Update the last back press time
        lastBackPressTime = currentTime;
    }
    
    /**
     * Open the configuration screen (UpdateActivity)
     */
    private void openConfigurationScreen() {
        Log.i(TAG, "Opening configuration screen");
        
        try {
            // Create intent to start UpdateActivity
            Intent configIntent = new Intent(this, UpdateActivity.class);
            
            // Don't mark it as first time setup since we're accessing from main app
            configIntent.putExtra("firstTimeSetup", false);
            
            // Start the configuration activity
            startActivity(configIntent);
            
            Log.i(TAG, "Successfully opened configuration screen");
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening configuration screen", e);
            Toast.makeText(this, "Error opening configuration", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Check if device ID is configured, redirect to update screen if needed
     */
    private void checkDeviceIdConfiguration() {
        if (!deviceIdManager.isDeviceIdConfigured()) {
            Log.i(TAG, "Device ID not configured, redirecting to update screen");
            // Redirect to UpdateActivity for first-time setup
            Intent updateIntent = new Intent(this, UpdateActivity.class);
            updateIntent.putExtra("firstTimeSetup", true);
            startActivity(updateIntent);
            finish(); // Close MainActivity
        } else {
            Log.i(TAG, "Device ID already configured: " + deviceIdManager.getDeviceId());
            // Configuration is complete, load the app
            loadConfiguration();
        }
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
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, orientations);
        orientationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orientationSpinner.setAdapter(orientationAdapter);
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
        // Set loading message while fetching device IDs
        List<String> loadingList = new ArrayList<>();
        loadingList.add("Loading device IDs...");
        ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, loadingList);
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        
        // Fetch device IDs in background
        executor.execute(() -> {
            try {
                List<String> deviceIds = fetchDeviceIdsFromGoogleSheets();
                
                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                        android.R.layout.simple_spinner_item, deviceIds);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to load device IDs for setup", e);
                runOnUiThread(() -> {
                    // Use fallback device IDs
                    List<String> fallbackIds = new ArrayList<>();
                    fallbackIds.add("Test");
                    fallbackIds.add("Production");
                    fallbackIds.add("Lobby");
                    
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                        android.R.layout.simple_spinner_item, fallbackIds);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                    
                    Toast.makeText(this, "Using fallback device IDs", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Fetch device IDs from Google Sheets
     */
    private List<String> fetchDeviceIdsFromGoogleSheets() throws Exception {
        List<String> deviceIds = new ArrayList<>();
        String csvUrl = "https://docs.google.com/spreadsheets/d/1vWzoYpMDIwfpAuwChbwinmoZxqwelO64ODOS97b27ag/export?format=csv&gid=0";
        
        URL url = new URL(csvUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        boolean firstRow = true;
        
        while ((line = reader.readLine()) != null) {
            if (firstRow) {
                firstRow = false;
                continue; // Skip header row
            }
            
            String[] parts = line.split(",");
            if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                String deviceId = parts[1].trim().replace("\"", "");
                if (!deviceId.equalsIgnoreCase("DeviceID")) {
                    deviceIds.add(deviceId);
                }
            }
        }
        
        reader.close();
        connection.disconnect();
        
        Log.i(TAG, "Loaded " + deviceIds.size() + " device IDs from Google Sheets for setup");
        return deviceIds;
    }
}
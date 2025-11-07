package com.kidsim.tvkiosk;

import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.kidsim.tvkiosk.config.ConfigurationManager;
import com.kidsim.tvkiosk.config.DeviceConfig;
import com.kidsim.tvkiosk.config.PageConfig;
import com.kidsim.tvkiosk.service.WatchdogService;
import java.util.List;

public class MainActivity extends Activity implements ConfigurationManager.ConfigUpdateListener {
    
    private static final String TAG = "MainActivity";
    private static final int MAX_PAGES = 3;
    
    // UI components
    private WebView[] webViews;
    private WebView[] backupWebViews;
    private LinearLayout loadingLayout;
    private LinearLayout errorLayout;
    private TextView loadingProgress;
    private TextView errorText;
    private Button retryButton;
    private Button updateButton;
    
    // Configuration and timing
    private ConfigurationManager configManager;
    private DeviceConfig currentConfig;
    private List<PageConfig> pages;
    private int currentPageIndex = 0;
    
    // Handlers for timing
    private Handler pageHandler;
    private Handler configHandler;
    private Handler retryHandler;
    private Handler refreshHandler;
    
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
        
        // Initialize WebView pool arrays
        pageLoadStates = new boolean[3];
        backupPageLoadStates = new boolean[3];
        
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
        // Initialize WebView pools
        webViews = new WebView[3];
        backupWebViews = new WebView[3];
        
        webViews[0] = findViewById(R.id.webView0);
        webViews[1] = findViewById(R.id.webView1);
        webViews[2] = findViewById(R.id.webView2);
        
        backupWebViews[0] = findViewById(R.id.backupWebView0);
        backupWebViews[1] = findViewById(R.id.backupWebView1);
        backupWebViews[2] = findViewById(R.id.backupWebView2);
        
        // Initialize loading UI
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        // Initialize error UI
        errorLayout = findViewById(R.id.errorLayout);
        errorText = findViewById(R.id.errorText);
        retryButton = findViewById(R.id.retryButton);
        updateButton = findViewById(R.id.updateButton);
        
        // Setup WebView pool
        setupWebViewPool();
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
        for (int i = 0; i < 3; i++) {
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
        
        // Check if initial load is complete
        if (!initialLoadComplete && pagesLoaded >= 1) {
            initialLoadComplete = true;
            showFirstPage();
        }
    }
    
    private void markPageFailed(WebView webView) {
        // Find which WebView this is and mark as failed
        for (int i = 0; i < 3; i++) {
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
        for (int i = 0; i < 3; i++) {
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
        // Hide all pages first
        for (int i = 0; i < 3; i++) {
            webViews[i].setVisibility(View.GONE);
            backupWebViews[i].setVisibility(View.GONE);
        }
        
        // Show the requested page
        if (pageIndex >= 0 && pageIndex < 3) {
            webViews[pageIndex].setVisibility(View.VISIBLE);
            currentPageIndex = pageIndex;
            Log.d(TAG, "Showing page: " + pageIndex);
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
        }, 5000); // 5 second delay between page refreshes
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
        
        for (int i = 0; i < 3; i++) {
            pageLoadStates[i] = false;
            backupPageLoadStates[i] = false;
        }
        
        // Show loading state
        showLoadingState();
        
        // Load up to 3 pages into the WebView pool
        int pagesToLoad = Math.min(pages.size(), 3);
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
        // Use the first page's display time for timing
        PageConfig currentPage = pages.get(currentPageIndex);
        long displayTime = currentPage.getDisplayTimeSeconds() * 1000L;
        
        pageRotationRunnable = () -> {
            // Move to next page
            int nextPageIndex = (currentPageIndex + 1) % Math.min(pages.size(), 3);
            
            // Switch to next page if it's loaded
            if (pageLoadStates[nextPageIndex]) {
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
        
        // Check network connectivity and update cache modes
        isNetworkConnected();
        for (int i = 0; i < 3; i++) {
            if (webViews[i] != null) {
                updateWebViewCacheMode(webViews[i].getSettings());
            }
            if (backupWebViews[i] != null) {
                updateWebViewCacheMode(backupWebViews[i].getSettings());
            }
        }
        
        // Setup pages
        pages = config.getPages();
        if (pages == null || pages.isEmpty()) {
            Log.w(TAG, "No pages configured, using default");
            showError("No pages configured");
            return;
        }
        
        // Load pages into WebView pool
        loadPagesIntoPool();
        
        // Start page rotation timer
        startPageRotationTimer();
        
        Log.i(TAG, "Configuration applied with " + pages.size() + " pages, network: " + 
              (isNetworkAvailable ? "CONNECTED" : "OFFLINE"));
    }
    
    private void applyOrientation(String orientation) {
        int orientationValue = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        
        if ("portrait".equalsIgnoreCase(orientation)) {
            orientationValue = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        
        setRequestedOrientation(orientationValue);
        Log.d(TAG, "Set orientation to: " + orientation);
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
        for (int i = 0; i < 3; i++) {
            webViews[i].setVisibility(View.GONE);
            backupWebViews[i].setVisibility(View.GONE);
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
            int totalPages = Math.min(pages.size(), 3);
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
        for (int i = 0; i < 3; i++) {
            if (webViews[i] != null) {
                webViews[i].onResume();
            }
            if (backupWebViews[i] != null) {
                backupWebViews[i].onResume();
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Pause all WebViews in the pool
        for (int i = 0; i < 3; i++) {
            if (webViews[i] != null) {
                webViews[i].onPause();
            }
            if (backupWebViews[i] != null) {
                backupWebViews[i].onPause();
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
}
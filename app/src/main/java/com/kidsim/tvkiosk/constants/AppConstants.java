package com.kidsim.tvkiosk.constants;

/**
 * Centralized constants class to eliminate magic values throughout the application
 */
public final class AppConstants {
    
    // Prevent instantiation
    private AppConstants() {}
    
    // === TIMING CONSTANTS ===
    public static final long RETRY_INTERVAL_MS = 300000L; // 5 minutes
    public static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L; // 10 minutes
    public static final long PAGE_ROTATION_DELAY_MS = 5000L; // 5 seconds
    public static final long DOWNLOAD_STATUS_CHECK_INTERVAL_MS = 2000L; // 2 seconds
    public static final long APK_INSTALL_DELAY_MS = 2000L; // 2 seconds
    public static final long SECONDS_TO_MILLISECONDS = 1000L;
    
    // === NETWORK TIMEOUTS ===
    public static final int HTTP_CONNECT_TIMEOUT_MS = 10000; // 10 seconds
    public static final int HTTP_READ_TIMEOUT_MS = 10000; // 10 seconds
    
    // === FILE SIZE CONSTANTS ===
    public static final int BYTES_PER_KB = 1024;
    public static final int KB_PER_MB = 1024;
    public static final long BYTES_PER_MB = 1024L * 1024L;
    public static final int DOWNLOAD_BUFFER_SIZE = 1024; // 1KB buffer
    public static final long MIN_VALID_FILE_SIZE = 1000L; // 1KB minimum for valid files
    
    // === MEMORY THRESHOLDS ===
    public static final long MAX_MEMORY_THRESHOLD_BYTES = 150 * BYTES_PER_MB; // 150MB
    public static final long WATCHDOG_INTERVAL_MS = 300000L; // 5 minutes
    
    // === REQUEST CODES ===
    public static final int INSTALL_PERMISSION_REQUEST_CODE = 1000;
    
    // === GITHUB API URLS ===
    public static final class GitHub {
        public static final String REPO_BASE = "KidsInternationalMinistries/KIDSAndroidTVKiosk";
        public static final String API_BASE = "https://api.github.com/repos/" + REPO_BASE;
        public static final String RELEASES_API_URL = API_BASE + "/releases";
        public static final String LATEST_RELEASE_API_URL = API_BASE + "/releases/latest";
        public static final String RELEASE_BASE_URL = "https://github.com/" + REPO_BASE + "/releases";
        public static final String TEST_APK_URL = RELEASE_BASE_URL + "/latest/download/app-test.apk";
        public static final String FALLBACK_DEBUG_APK_URL = RELEASE_BASE_URL + "/download/v1.0-test/app-test.apk";
        
        // Asset naming patterns
        public static final String RELEASE_APK_PATTERN = "app-release.apk";
        public static final String TEST_APK_PATTERN = "app-test.apk";
        public static final String DEBUG_APK_PATTERN = "app-debug.apk";
    }
    
    // === GOOGLE SHEETS API ===
    public static final class GoogleSheets {
        public static final String API_BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets/";
        public static final int MINIMUM_SHEET_ROWS = 5;
    }
    
    // === UI CONSTANTS ===
    public static final class UI {
        public static final String ORIENTATION_LANDSCAPE = "landscape";
        public static final String ORIENTATION_PORTRAIT = "portrait";
        
        // Default values
        public static final String DEFAULT_DEVICE_NAME = "Unknown Device";
        public static final String DEFAULT_VERSION = "Unknown";
        public static final int DEFAULT_DISPLAY_TIME_SECONDS = 30;
        public static final int DEFAULT_PAGE_ROTATION_INTERVAL_SECONDS = 60;
    }
    
    // === BUILD TYPES ===
    public static final class BuildTypes {
        public static final String RELEASE = "release";
        public static final String TEST = "test";  
        public static final String DEBUG = "debug";
    }
    
    // === PREFERENCE KEYS ===
    public static final class PreferenceKeys {
        public static final String CONFIG_JSON = "config_json";
        public static final String LAST_CONFIG_UPDATE = "last_config_update";
        public static final String DEVICE_ID = "device_id";
        public static final String BUILD_TYPE = "build_type";
        public static final String ORIENTATION = "orientation";
    }
    
    // === DOWNLOAD MANAGER ===
    public static final class Downloads {
        public static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
        public static final String DOWNLOADS_DIRECTORY = "/download/";
        public static final String APK_FILE_EXTENSION = ".apk";
    }
}
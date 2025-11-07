package com.kidsim.tvkiosk.config;

import java.util.List;

public class DeviceConfig {
    private String deviceId;
    private String deviceName;
    private String orientation; // "landscape" or "portrait"
    private int refreshIntervalMinutes;
    private List<PageConfig> pages;
    private boolean autoStart;
    private boolean clearCache;
    private String configVersion;
    
    // Default constructor
    public DeviceConfig() {
        this.orientation = "landscape";
        this.refreshIntervalMinutes = 60;
        this.autoStart = true;
        this.clearCache = false;
        this.configVersion = "1.0";
    }
    
    // Getters and setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    
    public int getRefreshIntervalMinutes() { return refreshIntervalMinutes; }
    public void setRefreshIntervalMinutes(int refreshIntervalMinutes) { 
        this.refreshIntervalMinutes = refreshIntervalMinutes; 
    }
    
    public List<PageConfig> getPages() { return pages; }
    public void setPages(List<PageConfig> pages) { this.pages = pages; }
    
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    
    public boolean isClearCache() { return clearCache; }
    public void setClearCache(boolean clearCache) { this.clearCache = clearCache; }
    
    public String getConfigVersion() { return configVersion; }
    public void setConfigVersion(String configVersion) { this.configVersion = configVersion; }
}
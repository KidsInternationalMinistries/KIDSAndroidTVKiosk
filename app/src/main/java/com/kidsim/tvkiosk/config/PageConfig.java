package com.kidsim.tvkiosk.config;

public class PageConfig {
    private String url;
    private int displayTimeSeconds;
    
    // Default constructor
    public PageConfig() {
        this.displayTimeSeconds = 300; // 5 minutes default
    }
    
    // Constructor with URL
    public PageConfig(String url) {
        this();
        this.url = url;
    }
    
    // Constructor with URL and display time
    public PageConfig(String url, int displayTimeSeconds) {
        this(url);
        this.displayTimeSeconds = displayTimeSeconds;
    }
    
    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public int getDisplayTimeSeconds() { return displayTimeSeconds; }
    public void setDisplayTimeSeconds(int displayTimeSeconds) { 
        this.displayTimeSeconds = displayTimeSeconds; 
    }
}
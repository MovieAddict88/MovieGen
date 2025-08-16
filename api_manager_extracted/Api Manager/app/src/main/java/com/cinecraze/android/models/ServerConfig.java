package com.cinecraze.android.models;

public class ServerConfig {
    
    private String name;
    private boolean enabled;
    private String quality;
    private String baseUrl;
    private boolean isOnline;
    private long lastChecked;

    // Constructors
    public ServerConfig() {
        this.enabled = false;
        this.quality = "720p";
        this.isOnline = false;
        this.lastChecked = 0;
    }

    public ServerConfig(String name) {
        this();
        this.name = name;
    }

    public ServerConfig(String name, boolean enabled, String quality) {
        this(name);
        this.enabled = enabled;
        this.quality = quality;
    }

    public ServerConfig(String name, String url, boolean enabled, String quality) {
        this(name, enabled, quality);
        this.baseUrl = url;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUrl() {
        return baseUrl;
    }

    public void setUrl(String url) {
        this.baseUrl = url;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
        this.lastChecked = System.currentTimeMillis();
    }

    public boolean isWorking() {
        return isOnline;
    }

    public void setWorking(boolean working) {
        setOnline(working);
    }

    public long getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(long lastChecked) {
        this.lastChecked = lastChecked;
    }

    // Helper methods
    public String getDisplayName() {
        return name;
    }

    public String getStatusText() {
        if (!enabled) {
            return "Disabled";
        }
        return isOnline ? "Online" : "Offline";
    }

    public boolean needsStatusCheck() {
        // Check status every 5 minutes
        return System.currentTimeMillis() - lastChecked > 5 * 60 * 1000;
    }

    public String generateEmbedUrl(String title) {
        if (!enabled || baseUrl == null || baseUrl.isEmpty()) {
            return null;
        }
        
        // Generate embed URL based on server type
        String encodedTitle = java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);
        
        switch (name.toLowerCase()) {
            case "vidsrc":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidjoy":
                return baseUrl + "/embed/" + encodedTitle;
            case "multiembed":
                return baseUrl + "/embed/" + encodedTitle;
            case "embed.su":
                return baseUrl + "/embed/" + encodedTitle;
            case "autoembed.cc":
                return baseUrl + "/embed/" + encodedTitle;
            case "smashystream":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidsrc.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidsrc.xyz":
                return baseUrl + "/embed/" + encodedTitle;
            case "embedsoap":
                return baseUrl + "/embed/" + encodedTitle;
            case "moviesapi.club":
                return baseUrl + "/embed/" + encodedTitle;
            case "dbgo.fun":
                return baseUrl + "/embed/" + encodedTitle;
            case "flixhq.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "gomovies.sx":
                return baseUrl + "/embed/" + encodedTitle;
            case "showbox.media":
                return baseUrl + "/embed/" + encodedTitle;
            case "primewire.mx":
                return baseUrl + "/embed/" + encodedTitle;
            case "hdtoday.tv":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidcloud.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "streamwish.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "doodstream.com":
                return baseUrl + "/embed/" + encodedTitle;
            case "streamtape.com":
                return baseUrl + "/embed/" + encodedTitle;
            case "mixdrop.co":
                return baseUrl + "/embed/" + encodedTitle;
            case "filemoon.sx":
                return baseUrl + "/embed/" + encodedTitle;
            case "upstream.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "godriveplayer.com":
                return baseUrl + "/embed/" + encodedTitle;
            case "2embed.cc":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidlink.pro":
                return baseUrl + "/embed/" + encodedTitle;
            default:
                return baseUrl + "/embed/" + encodedTitle;
        }
    }

    public String getRecommendedBaseUrl() {
        switch (name.toLowerCase()) {
            case "vidsrc":
                return "https://vidsrc.to";
            case "vidjoy":
                return "https://vidjoy.to";
            case "multiembed":
                return "https://multiembed.mov";
            case "embed.su":
                return "https://embed.su";
            case "autoembed.cc":
                return "https://autoembed.cc";
            case "smashystream":
                return "https://smashystream.com";
            case "vidsrc.to":
                return "https://vidsrc.to";
            case "vidsrc.xyz":
                return "https://vidsrc.xyz";
            case "embedsoap":
                return "https://embedsoap.com";
            case "moviesapi.club":
                return "https://moviesapi.club";
            case "dbgo.fun":
                return "https://dbgo.fun";
            case "flixhq.to":
                return "https://flixhq.to";
            case "gomovies.sx":
                return "https://gomovies.sx";
            case "showbox.media":
                return "https://showbox.media";
            case "primewire.mx":
                return "https://primewire.mx";
            case "hdtoday.tv":
                return "https://hdtoday.tv";
            case "vidcloud.to":
                return "https://vidcloud.to";
            case "streamwish.to":
                return "https://streamwish.to";
            case "doodstream.com":
                return "https://doodstream.com";
            case "streamtape.com":
                return "https://streamtape.com";
            case "mixdrop.co":
                return "https://mixdrop.co";
            case "filemoon.sx":
                return "https://filemoon.sx";
            case "upstream.to":
                return "https://upstream.to";
            case "godriveplayer.com":
                return "https://godriveplayer.com";
            case "2embed.cc":
                return "https://2embed.cc";
            case "vidlink.pro":
                return "https://vidlink.pro";
            default:
                return "https://" + name.toLowerCase() + ".com";
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ServerConfig that = (ServerConfig) obj;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "name='" + name + '\'' +
                ", enabled=" + enabled +
                ", quality='" + quality + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }
}
package com.cinecraze.android.services;

import android.util.Log;
import com.cinecraze.android.models.ServerConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoEmbedService {
    
    private static final String TAG = "AutoEmbedService";
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final java.util.Map<String, ServerStatusCache> statusCache;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes
    
    private static class ServerStatusCache {
        final boolean isOnline;
        final long timestamp;
        
        ServerStatusCache(boolean isOnline) {
            this.isOnline = isOnline;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_DURATION;
        }
    }

    public AutoEmbedService() {
        this.client = new OkHttpClient();
        this.executor = Executors.newFixedThreadPool(5);
        this.statusCache = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public List<String> generateAutoEmbedUrls(String title, List<ServerConfig> enabledServers) {
        List<String> urls = new ArrayList<>();
        
        if (title == null || title.trim().isEmpty()) {
            Log.w(TAG, "Title is null or empty");
            return urls;
        }
        
        String encodedTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8);
        
        for (ServerConfig server : enabledServers) {
            if (server.isEnabled()) {
                String url = generateUrlForServer(server, encodedTitle);
                if (url != null) {
                    // Format: "ServerName|URL"
                    urls.add(server.getName() + "|" + url);
                }
            }
        }
        
        Log.i(TAG, "Generated " + urls.size() + " auto-embed URLs for: " + title);
        return urls;
    }

    private String generateUrlForServer(ServerConfig server, String encodedTitle) {
        String baseUrl = server.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = server.getRecommendedBaseUrl();
        }
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            Log.w(TAG, "No base URL for server: " + server.getName());
            return null;
        }
        
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        switch (server.getName().toLowerCase()) {
            case "vidsrc":
            case "vidsrc.to":
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
            case "vidsrc.xyz":
                return baseUrl + "/embed/" + encodedTitle;
            case "embedsoap":
                return baseUrl + "/embed/" + encodedTitle;
            case "moviesapi.club":
                return baseUrl + "/embed/" + encodedTitle;
            case "dbgo.fun":
                return baseUrl + "/embed/" + encodedTitle;
            case "flixhq.to":
                return baseUrl + "/watch/" + encodedTitle;
            case "gomovies.sx":
                return baseUrl + "/watch/" + encodedTitle;
            case "showbox.media":
                return baseUrl + "/embed/" + encodedTitle;
            case "primewire.mx":
                return baseUrl + "/embed/" + encodedTitle;
            case "hdtoday.tv":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidcloud.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "streamwish.to":
                return baseUrl + "/e/" + encodedTitle;
            case "doodstream.com":
                return baseUrl + "/e/" + encodedTitle;
            case "streamtape.com":
                return baseUrl + "/e/" + encodedTitle;
            case "mixdrop.co":
                return baseUrl + "/e/" + encodedTitle;
            case "filemoon.sx":
                return baseUrl + "/e/" + encodedTitle;
            case "upstream.to":
                return baseUrl + "/" + encodedTitle;
            case "godriveplayer.com":
                return baseUrl + "/embed/" + encodedTitle;
            case "2embed.cc":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidlink.pro":
                return baseUrl + "/movie/" + encodedTitle;
            case "streamlare.com":
                return baseUrl + "/e/" + encodedTitle;
            case "streamhub.to":
                return baseUrl + "/e/" + encodedTitle;
            case "nontonfilm":
                return baseUrl + "/embed/" + encodedTitle;
            case "cataz":
                return baseUrl + "/embed/" + encodedTitle;
            default:
                // Generic fallback
                return baseUrl + "/embed/" + encodedTitle;
        }
    }

    public boolean checkServerStatus(String serverName) {
        return checkServerStatus(serverName, 3); // Default 3 retries
    }
    
    public boolean checkServerStatus(String serverName, int maxRetries) {
        // Check cache first
        ServerStatusCache cached = statusCache.get(serverName);
        if (cached != null && cached.isValid()) {
            Log.d(TAG, "Server " + serverName + " status from cache: " + (cached.isOnline ? "Online" : "Offline"));
            return cached.isOnline;
        }
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Generate a test embed URL for a popular movie
                String testTitle = "The Matrix";
                String encodedTitle = URLEncoder.encode(testTitle, StandardCharsets.UTF_8);
                String embedUrl = generateTestEmbedUrl(serverName, encodedTitle);
                
                if (embedUrl == null) {
                    Log.w(TAG, "Could not generate test URL for server: " + serverName);
                    return false;
                }
                
                // Create request with timeout
                OkHttpClient timeoutClient = client.newBuilder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
                
                Request request = new Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "en-US,en;q=0.5")
                    .addHeader("Accept-Encoding", "gzip, deflate")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Upgrade-Insecure-Requests", "1")
                    .build();
                
                try (Response response = timeoutClient.newCall(request).execute()) {
                    boolean isOnline = response.isSuccessful() && validateEmbedResponse(response, serverName);
                    
                    if (isOnline) {
                        Log.d(TAG, "Server " + serverName + " status: Online (attempt " + attempt + ")");
                        statusCache.put(serverName, new ServerStatusCache(true));
                        return true;
                    } else {
                        Log.w(TAG, "Server " + serverName + " status: Offline (attempt " + attempt + ") - Response: " + response.code());
                    }
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Error checking status for server " + serverName + " (attempt " + attempt + "): " + e.getMessage());
                
                // If this is the last attempt, return false
                if (attempt == maxRetries) {
                    return false;
                }
                
                // Wait a bit before retrying
                try {
                    Thread.sleep(1000 * attempt); // Progressive delay: 1s, 2s, 3s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        Log.w(TAG, "Server " + serverName + " failed all " + maxRetries + " attempts");
        statusCache.put(serverName, new ServerStatusCache(false));
        return false;
    }
    
    private String generateTestEmbedUrl(String serverName, String encodedTitle) {
        String baseUrl = getBaseUrlForServer(serverName);
        if (baseUrl == null) {
            return null;
        }
        
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        switch (serverName.toLowerCase()) {
            case "vidsrc":
            case "vidsrc.to":
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
            case "vidsrc.xyz":
                return baseUrl + "/embed/" + encodedTitle;
            case "embedsoap":
                return baseUrl + "/embed/" + encodedTitle;
            case "moviesapi.club":
                return baseUrl + "/embed/" + encodedTitle;
            case "dbgo.fun":
                return baseUrl + "/embed/" + encodedTitle;
            case "flixhq.to":
                return baseUrl + "/watch/" + encodedTitle;
            case "gomovies.sx":
                return baseUrl + "/watch/" + encodedTitle;
            case "showbox.media":
                return baseUrl + "/embed/" + encodedTitle;
            case "primewire.mx":
                return baseUrl + "/embed/" + encodedTitle;
            case "hdtoday.tv":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidcloud.to":
                return baseUrl + "/embed/" + encodedTitle;
            case "streamwish.to":
                return baseUrl + "/e/" + encodedTitle;
            case "doodstream.com":
                return baseUrl + "/e/" + encodedTitle;
            case "streamtape.com":
                return baseUrl + "/e/" + encodedTitle;
            case "mixdrop.co":
                return baseUrl + "/e/" + encodedTitle;
            case "filemoon.sx":
                return baseUrl + "/e/" + encodedTitle;
            case "upstream.to":
                return baseUrl + "/" + encodedTitle;
            case "godriveplayer.com":
                return baseUrl + "/embed/" + encodedTitle;
            case "2embed.cc":
                return baseUrl + "/embed/" + encodedTitle;
            case "vidlink.pro":
                return baseUrl + "/movie/" + encodedTitle;
            case "streamlare.com":
                return baseUrl + "/e/" + encodedTitle;
            case "streamhub.to":
                return baseUrl + "/e/" + encodedTitle;
            case "nontonfilm":
                return baseUrl + "/embed/" + encodedTitle;
            case "cataz":
                return baseUrl + "/embed/" + encodedTitle;
            default:
                return baseUrl + "/embed/" + encodedTitle;
        }
    }
    
    private boolean validateEmbedResponse(Response response, String serverName) {
        try {
            // Check if response is successful
            if (!response.isSuccessful()) {
                return false;
            }
            
            // Check content type
            String contentType = response.header("Content-Type");
            if (contentType != null && contentType.contains("text/html")) {
                // For HTML responses, check if it's not an error page
                String body = response.body().string();
                
                // Check for common error indicators
                String lowerBody = body.toLowerCase();
                if (lowerBody.contains("404") || 
                    lowerBody.contains("not found") || 
                    lowerBody.contains("error") ||
                    lowerBody.contains("page not found") ||
                    lowerBody.contains("server error")) {
                    return false;
                }
                
                // Check for positive indicators (embed content, video player, etc.)
                if (lowerBody.contains("video") || 
                    lowerBody.contains("player") || 
                    lowerBody.contains("embed") ||
                    lowerBody.contains("iframe") ||
                    lowerBody.contains("stream") ||
                    lowerBody.contains("watch")) {
                    return true;
                }
                
                // If we can't determine, assume it's working if we got a successful response
                return true;
            }
            
            // For non-HTML responses, assume it's working if we got a successful response
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Error validating response for " + serverName + ": " + e.getMessage());
            return false;
        }
    }

    public void checkServerStatusAsync(String serverName, ServerStatusCallback callback) {
        executor.submit(() -> {
            boolean isOnline = checkServerStatus(serverName);
            callback.onResult(serverName, isOnline);
        });
    }

    public void checkMultipleServerStatus(List<String> serverNames, MultipleServerStatusCallback callback) {
        executor.submit(() -> {
            List<ServerStatus> results = new ArrayList<>();
            
            for (String serverName : serverNames) {
                boolean isOnline = checkServerStatus(serverName);
                results.add(new ServerStatus(serverName, isOnline));
            }
            
            callback.onComplete(results);
        });
    }

    private String getBaseUrlForServer(String serverName) {
        switch (serverName.toLowerCase()) {
            case "vidsrc":
                return "https://vidsrc.to";
            case "vidjoy":
                return "https://vidjoy.pro";
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
                return null;
        }
    }

    public List<String> getRecommendedServers() {
        List<String> recommended = new ArrayList<>();
        recommended.add("VidSrc");
        recommended.add("VidJoy");
        recommended.add("MultiEmbed");
        recommended.add("AutoEmbed.cc");
        recommended.add("VidSrc.to");
        return recommended;
    }

    public List<String> getAllServers() {
        List<String> allServers = new ArrayList<>();
        allServers.add("VidSrc");
        allServers.add("VidJoy");
        allServers.add("MultiEmbed");
        allServers.add("Embed.su");
        allServers.add("AutoEmbed.cc");
        allServers.add("SmashyStream");
        allServers.add("VidSrc.to");
        allServers.add("VidSrc.xyz");
        allServers.add("EmbedSoap");
        allServers.add("MoviesAPI.club");
        allServers.add("DBGO.fun");
        allServers.add("FlixHQ.to");
        allServers.add("GoMovies.sx");
        allServers.add("ShowBox.media");
        allServers.add("PrimeWire.mx");
        allServers.add("HDToday.tv");
        allServers.add("VidCloud.to");
        allServers.add("StreamWish.to");
        allServers.add("DoodStream.com");
        allServers.add("StreamTape.com");
        allServers.add("MixDrop.co");
        allServers.add("FileMoon.sx");
        allServers.add("UpStream.to");
        allServers.add("GoDrivePlayer.com");
        allServers.add("2Embed.cc");
        allServers.add("VidLink.pro");
        return allServers;
    }

    public interface ServerStatusCallback {
        void onResult(String serverName, boolean isOnline);
    }

    public interface MultipleServerStatusCallback {
        void onComplete(List<ServerStatus> results);
    }

    public static class ServerStatus {
        private String serverName;
        private boolean isOnline;

        public ServerStatus(String serverName, boolean isOnline) {
            this.serverName = serverName;
            this.isOnline = isOnline;
        }

        public String getServerName() {
            return serverName;
        }

        public boolean isOnline() {
            return isOnline;
        }
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    public void clearStatusCache() {
        statusCache.clear();
        Log.d(TAG, "Server status cache cleared");
    }
    
    public void clearStatusCache(String serverName) {
        statusCache.remove(serverName);
        Log.d(TAG, "Server status cache cleared for: " + serverName);
    }
}
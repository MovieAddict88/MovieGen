package com.cinecraze.android.utils;

import com.cinecraze.android.models.ContentItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to handle JSON structure based on the GitHub repository format:
 * https://github.com/MovieAddict88/Movie-Source/raw/main/playlist.json
 */
public class JsonStructure {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Creates the JSON structure for export based on the GitHub repository format
     */
    public static String createExportJson(List<ContentItem> contentItems) {
        try {
            JsonObject root = new JsonObject();
            JsonArray categories = new JsonArray();
            
            // Group content items by main category
            Map<String, List<ContentItem>> categoryMap = new HashMap<>();
            for (ContentItem item : contentItems) {
                String mainCategory = getMainCategory(item.getType());
                categoryMap.computeIfAbsent(mainCategory, k -> new ArrayList<>()).add(item);
            }
            
            // Create categories
            for (Map.Entry<String, List<ContentItem>> entry : categoryMap.entrySet()) {
                String mainCategory = entry.getKey();
                List<ContentItem> items = entry.getValue();
                
                JsonObject category = new JsonObject();
                category.addProperty("MainCategory", mainCategory);
                
                // Get unique subcategories
                Set<String> subCategories = new HashSet<>();
                for (ContentItem item : items) {
                    if (item.getSubcategory() != null && !item.getSubcategory().isEmpty()) {
                        subCategories.add(item.getSubcategory());
                    }
                }
                
                JsonArray subCategoriesArray = new JsonArray();
                for (String subCategory : subCategories) {
                    subCategoriesArray.add(subCategory);
                }
                category.add("SubCategories", subCategoriesArray);
                
                // Create entries
                JsonArray entries = new JsonArray();
                for (ContentItem item : items) {
                    JsonObject entryObject = new JsonObject();
                    
                    entryObject.addProperty("Title", item.getTitle());
                    entryObject.addProperty("SubCategory", item.getSubcategory() != null ? item.getSubcategory() : "");
                    entryObject.addProperty("Country", item.getCountry() != null ? item.getCountry() : "");
                    entryObject.addProperty("Description", item.getDescription() != null ? item.getDescription() : "");
                    entryObject.addProperty("Poster", item.getImageUrl() != null ? item.getImageUrl() : "");
                    entryObject.addProperty("Thumbnail", item.getImageUrl() != null ? item.getImageUrl() : "");
                    entryObject.addProperty("Rating", item.getRating() != null ? item.getRating() : 0);
                    entryObject.addProperty("Duration", ""); // Not stored in our model
                    entryObject.addProperty("Year", item.getYear() != null ? item.getYear() : 0);
                    
                    // Create servers array
                    if (item.getServers() != null && !item.getServers().isEmpty()) {
                        JsonArray serversArray = new JsonArray();
                        for (String serverUrl : item.getServers()) {
                            JsonObject server = new JsonObject();
                            server.addProperty("name", getServerName(serverUrl));
                            server.addProperty("url", serverUrl);
                            // Note: We don't add DRM fields since our app doesn't support DRM
                            // This ensures compatibility with non-DRM players
                            serversArray.add(server);
                        }
                        entryObject.add("Servers", serversArray);
                    }
                    
                    entries.add(entryObject);
                }
                
                category.add("Entries", entries);
                categories.add(category);
            }
            
            root.add("Categories", categories);
            
            return gson.toJson(root);
            
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    /**
     * Parses the JSON structure from the GitHub repository format
     */
    public static List<ContentItem> parseImportJson(String jsonString) {
        return parseImportJson(jsonString, null);
    }

    /**
     * Parses the JSON structure from the GitHub repository format with DRM filtering feedback
     */
    public static List<ContentItem> parseImportJson(String jsonString, ImportResultCallback callback) {
        List<ContentItem> contentItems = new ArrayList<>();
        int totalServers = 0;
        int drmFilteredServers = 0;
        
        try {
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray categories = root.getAsJsonArray("Categories");
            
            if (categories != null) {
                for (int i = 0; i < categories.size(); i++) {
                    JsonObject category = categories.get(i).getAsJsonObject();
                    JsonArray entries = category.getAsJsonArray("Entries");
                    
                    if (entries != null) {
                        for (int j = 0; j < entries.size(); j++) {
                            JsonObject entry = entries.get(j).getAsJsonObject();
                            ContentItem item = new ContentItem();
                            
                            // Basic information
                            if (entry.has("Title")) item.setTitle(getStringValue(entry, "Title"));
                            if (entry.has("SubCategory")) item.setSubcategory(getStringValue(entry, "SubCategory"));
                            if (entry.has("Country")) item.setCountry(getStringValue(entry, "Country"));
                            if (entry.has("Description")) item.setDescription(getStringValue(entry, "Description"));
                            if (entry.has("Poster")) item.setImageUrl(getStringValue(entry, "Poster"));
                            if (entry.has("Year")) item.setYear(getIntValue(entry, "Year"));
                            if (entry.has("Rating")) item.setRating(getDoubleValue(entry, "Rating"));
                            
                            // Set type based on main category
                            String mainCategory = getStringValue(category, "MainCategory");
                            item.setType(mainCategory);
                            
                            // Servers array - handle DRM fields safely
                            if (entry.has("Servers")) {
                                JsonArray serversArray = entry.getAsJsonArray("Servers");
                                List<String> servers = new ArrayList<>();
                                for (int k = 0; k < serversArray.size(); k++) {
                                    JsonObject server = serversArray.get(k).getAsJsonObject();
                                    if (server.has("url")) {
                                        totalServers++;
                                        String serverUrl = getStringValue(server, "url");
                                        // Only add non-DRM servers
                                        if (serverUrl != null && !serverUrl.isEmpty()) {
                                            // Check if this is a DRM-protected server
                                            boolean isDrm = isDrmProtected(server);
                                            
                                            // Only add non-DRM servers to our list
                                            if (!isDrm) {
                                                servers.add(serverUrl);
                                            } else {
                                                drmFilteredServers++;
                                            }
                                        }
                                    }
                                }
                                item.setServers(servers);
                            }
                            
                            contentItems.add(item);
                        }
                    }
                }
            }
            
            // Provide feedback about DRM filtering
            if (callback != null && totalServers > 0) {
                callback.onImportComplete(contentItems, totalServers, drmFilteredServers);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return contentItems;
    }

    /**
     * Callback interface for import results with DRM filtering feedback
     */
    public interface ImportResultCallback {
        void onImportComplete(List<ContentItem> importedItems, int totalServers, int drmFilteredServers);
    }
    
    /**
     * Creates a sample JSON structure for testing
     */
    public static String createSampleJson() {
        List<ContentItem> sampleItems = new ArrayList<>();
        
        // Sample Movie
        ContentItem movie = new ContentItem();
        movie.setTitle("Fight Club");
        movie.setType("Movies");
        movie.setSubcategory("Drama");
        movie.setCountry("USA");
        movie.setDescription("An insomniac office worker and a devil-may-care soapmaker form an underground fight club that evolves into something much, much more.");
        movie.setImageUrl("https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg");
        movie.setYear(1999);
        movie.setRating(8.8);
        movie.setTmdbId(550);
        
        List<String> movieServers = new ArrayList<>();
        movieServers.add("https://vidsrc.to/embed/Fight%20Club");
        movieServers.add("https://vidjoy.to/embed/Fight%20Club");
        movie.setServers(movieServers);
        
        sampleItems.add(movie);
        
        // Sample TV Series
        ContentItem series = new ContentItem();
        series.setTitle("Game of Thrones S01");
        series.setType("TV Series");
        series.setSubcategory("Fantasy");
        series.setCountry("USA");
        series.setDescription("Nine noble families fight for control over the lands of Westeros, while an ancient enemy returns after being dormant for millennia.");
        series.setImageUrl("https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg");
        series.setYear(2011);
        series.setRating(9.3);
        series.setTmdbId(1399);
        series.setSeason(1);
        
        List<String> seriesServers = new ArrayList<>();
        seriesServers.add("https://vidsrc.to/embed/Game%20of%20Thrones");
        seriesServers.add("https://vidjoy.to/embed/Game%20of%20Thrones");
        series.setServers(seriesServers);
        
        sampleItems.add(series);
        
        return createExportJson(sampleItems);
    }

    /**
     * Helper method to get main category from content type
     */
    private static String getMainCategory(String type) {
        if (type == null) return "Other";
        
        switch (type.toLowerCase()) {
            case "movie":
            case "movies":
                return "Movies";
            case "tv series":
            case "tv":
            case "series":
                return "TV Series";
            case "live tv":
            case "live":
                return "Live TV";
            default:
                return "Other";
        }
    }

    /**
     * Helper method to get server name from URL
     */
    private static String getServerName(String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return "Unknown Server";
        }
        
        // Extract domain name from URL
        try {
            String domain = serverUrl.replaceAll("https?://", "").split("/")[0];
            if (domain.contains(".")) {
                String name = domain.split("\\.")[0];
                return name.substring(0, 1).toUpperCase() + name.substring(1) + " 1080p";
            }
            return domain + " 1080p";
        } catch (Exception e) {
            return "Server 1080p";
        }
    }

    /**
     * Helper method to check if a server has DRM protection
     */
    private static boolean isDrmProtected(JsonObject server) {
        try {
            // Check for DRM flag
            if (server.has("drm")) {
                try {
                    return server.get("drm").getAsBoolean();
                } catch (Exception e) {
                    // If drm field is not boolean, check if it's a truthy string
                    String drmValue = getStringValue(server, "drm");
                    return drmValue != null && (drmValue.equalsIgnoreCase("true") || 
                                               drmValue.equals("1") || 
                                               drmValue.equalsIgnoreCase("yes"));
                }
            }
            
            // Check for license field (indicates DRM)
            if (server.has("license")) {
                String license = getStringValue(server, "license");
                return license != null && !license.isEmpty();
            }
            
            return false;
        } catch (Exception e) {
            // If any error occurs, assume no DRM for safety
            return false;
        }
    }
    
    // Helper methods for safe JSON parsing
    private static String getStringValue(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Integer getIntValue(JsonObject json, String key) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                if (json.get(key).isJsonPrimitive()) {
                    if (json.get(key).getAsJsonPrimitive().isNumber()) {
                        return json.get(key).getAsInt();
                    } else if (json.get(key).getAsJsonPrimitive().isString()) {
                        String value = json.get(key).getAsString();
                        try {
                            return Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // Try to extract year from date string (YYYY-MM-DD format)
                            if (value != null && value.length() >= 4) {
                                try {
                                    return Integer.parseInt(value.substring(0, 4));
                                } catch (NumberFormatException e2) {
                                    return null;
                                }
                            }
                            return null;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Double getDoubleValue(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Long getLongValue(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
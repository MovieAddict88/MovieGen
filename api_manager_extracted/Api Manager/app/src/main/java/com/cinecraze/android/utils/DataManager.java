package com.cinecraze.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.net.Uri;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class DataManager {
    
    private static final String TAG = "DataManager";
    private static final String PREF_NAME = "cinecraze_data";
    private static final String KEY_CONTENT_ITEMS = "content_items";
    private static final String KEY_SERVER_CONFIGS = "server_configs";
    private static final String KEY_GITHUB_TOKEN = "github_token";
    private static final String KEY_GITHUB_REPO = "github_repo";
    private static final String KEY_GITHUB_FILE_PATH = "github_file_path";
    private static final String KEY_TMDB_API_KEY = "tmdb_api_key";
    
    private static DataManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private List<ContentItem> contentItems;
    private List<ServerConfig> serverConfigs;

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context.getApplicationContext());
        }
        return instance;
    }

    private DataManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.contentItems = new ArrayList<>();
        this.serverConfigs = new ArrayList<>();
        loadData();
    }

    // Content Items Management
    public void addContentItem(ContentItem item) {
        if (item != null) {
            contentItems.add(item);
            saveContentItems();
        }
    }

    public void addContent(ContentItem item) {
        addContentItem(item);
    }

    public void addContentItems(List<ContentItem> items) {
        if (items != null) {
            contentItems.addAll(items);
            saveContentItems();
        }
    }

    public void addContentList(List<ContentItem> items) {
        addContentItems(items);
    }

    public List<ContentItem> getAllContentItems() {
        return new ArrayList<>(contentItems);
    }

    public List<ContentItem> getAllContent() {
        return getAllContentItems();
    }

    public List<ContentItem> getContentItemsByType(String type) {
        List<ContentItem> filtered = new ArrayList<>();
        for (ContentItem item : contentItems) {
            if (type.equals(item.getType())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public void removeContentItem(ContentItem item) {
        contentItems.remove(item);
        saveContentItems();
    }

    public void updateContent(ContentItem item) {
        // First try by non-zero id
        if (item.getId() != 0) {
            for (int i = 0; i < contentItems.size(); i++) {
                if (contentItems.get(i).getId() == item.getId()) {
                    contentItems.set(i, item);
                    saveContentItems();
                    return;
                }
            }
        }
        // Fallback: by equality (title, type, tmdbId, season, episode)
        for (int i = 0; i < contentItems.size(); i++) {
            if (contentItems.get(i).equals(item)) {
                contentItems.set(i, item);
                saveContentItems();
                return;
            }
        }
        // Last resort: match by tmdbId + season + episode + type when available
        for (int i = 0; i < contentItems.size(); i++) {
            ContentItem existing = contentItems.get(i);
            boolean match = true;
            if (item.getTmdbId() != null && existing.getTmdbId() != null) {
                match &= item.getTmdbId().equals(existing.getTmdbId());
            }
            if (item.getSeason() != null && existing.getSeason() != null) {
                match &= item.getSeason().equals(existing.getSeason());
            }
            if (item.getEpisode() != null && existing.getEpisode() != null) {
                match &= item.getEpisode().equals(existing.getEpisode());
            }
            if (item.getType() != null && existing.getType() != null) {
                match &= item.getType().equals(existing.getType());
            }
            if (item.getSeriesTitle() != null && existing.getSeriesTitle() != null) {
                match &= item.getSeriesTitle().equals(existing.getSeriesTitle());
            }
            if (match) {
                contentItems.set(i, item);
                saveContentItems();
                return;
            }
        }
        // If nothing matched, append it
        contentItems.add(item);
        saveContentItems();
    }

    public void clearAllContent() {
        contentItems.clear();
        saveContentItems();
    }

    public void clearAllData() {
        clearAllContent();
        clearServerConfigs();
    }

    public int removeDuplicates() {
        int originalSize = contentItems.size();
        Set<ContentItem> uniqueItems = new HashSet<>(contentItems);
        contentItems = new ArrayList<>(uniqueItems);
        saveContentItems();
        return originalSize - contentItems.size();
    }

    public boolean isDuplicate(ContentItem item) {
        if (item == null) return false;
        
        for (ContentItem existing : contentItems) {
            // Check for exact equals match first (uses title, type, tmdbId, season, episode)
            if (existing.equals(item)) {
                return true;
            }
            
            // Additional duplicate checks for TMDB content
            if (item.getTmdbId() != null && existing.getTmdbId() != null && 
                item.getTmdbId().equals(existing.getTmdbId()) &&
                item.getType() != null && existing.getType() != null &&
                item.getType().equals(existing.getType())) {
                
                // For TV series, also check season and episode
                if ("TV Series".equals(item.getType())) {
                    boolean seasonMatch = (item.getSeason() == null && existing.getSeason() == null) ||
                                        (item.getSeason() != null && item.getSeason().equals(existing.getSeason()));
                    boolean episodeMatch = (item.getEpisode() == null && existing.getEpisode() == null) ||
                                         (item.getEpisode() != null && item.getEpisode().equals(existing.getEpisode()));
                    boolean seriesTitleMatch = (item.getSeriesTitle() == null && existing.getSeriesTitle() == null) ||
                                             (item.getSeriesTitle() != null && item.getSeriesTitle().equals(existing.getSeriesTitle()));
                    if (seasonMatch && episodeMatch && seriesTitleMatch) {
                        return true;
                    }
                } else {
                    // For movies and other content, TMDB ID + type is enough
                    return true;
                }
            }
            
            // Fallback: check title similarity for content without TMDB ID
            if (item.getTmdbId() == null || existing.getTmdbId() == null) {
                if (item.getTitle() != null && existing.getTitle() != null &&
                    item.getType() != null && existing.getType() != null &&
                    item.getTitle().trim().equalsIgnoreCase(existing.getTitle().trim()) &&
                    item.getType().equals(existing.getType())) {
                    
                    // Additional checks for TV series
                    if ("TV Series".equals(item.getType())) {
                        boolean seasonMatch = (item.getSeason() == null && existing.getSeason() == null) ||
                                            (item.getSeason() != null && item.getSeason().equals(existing.getSeason()));
                        boolean episodeMatch = (item.getEpisode() == null && existing.getEpisode() == null) ||
                                             (item.getEpisode() != null && item.getEpisode().equals(existing.getEpisode()));
                        boolean seriesTitleMatch = (item.getSeriesTitle() == null && existing.getSeriesTitle() == null) ||
                                                 (item.getSeriesTitle() != null && item.getSeriesTitle().equals(existing.getSeriesTitle()));
                        if (seasonMatch && episodeMatch && seriesTitleMatch) {
                            return true;
                        }
                    } else {
                        // For movies, also check year if available
                        if (item.getYear() != null && existing.getYear() != null) {
                            return item.getYear().equals(existing.getYear());
                        } else {
                            return true; // Title + type match for content without year
                        }
                    }
                }
            }
        }
        
        return false;
    }

    // Server Configs Management
    public void addServerConfig(ServerConfig config) {
        if (config != null) {
            serverConfigs.add(config);
            saveServerConfigs();
        }
    }

    public List<ServerConfig> getAllServerConfigs() {
        return new ArrayList<>(serverConfigs);
    }

    public List<ServerConfig> getEnabledServerConfigs() {
        List<ServerConfig> enabled = new ArrayList<>();
        for (ServerConfig config : serverConfigs) {
            if (config.isEnabled()) {
                enabled.add(config);
            }
        }
        return enabled;
    }

    public void updateServerConfig(ServerConfig config) {
        for (int i = 0; i < serverConfigs.size(); i++) {
            if (serverConfigs.get(i).getName().equals(config.getName())) {
                serverConfigs.set(i, config);
                break;
            }
        }
        saveServerConfigs();
    }

    public void saveServerConfigs(List<ServerConfig> configs) {
        this.serverConfigs = new ArrayList<>(configs);
        saveServerConfigs();
    }

    public void clearServerConfigs() {
        serverConfigs.clear();
        saveServerConfigs();
    }

    // Enhanced GitHub Settings
    public void saveGitHubConfig(String token, String repo, String filePath) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_GITHUB_TOKEN, token);
        editor.putString(KEY_GITHUB_REPO, repo);
        editor.putString(KEY_GITHUB_FILE_PATH, filePath);
        editor.apply();
    }

    public void saveGitHubToken(String token) {
        preferences.edit().putString(KEY_GITHUB_TOKEN, token).apply();
    }

    public String getGitHubToken() {
        return preferences.getString(KEY_GITHUB_TOKEN, "");
    }

    public void saveGitHubRepo(String repo) {
        preferences.edit().putString(KEY_GITHUB_REPO, repo).apply();
    }

    public String getGitHubRepo() {
        return preferences.getString(KEY_GITHUB_REPO, "");
    }

    public void saveGitHubFilePath(String filePath) {
        preferences.edit().putString(KEY_GITHUB_FILE_PATH, filePath).apply();
    }

    public String getGitHubFilePath() {
        return preferences.getString(KEY_GITHUB_FILE_PATH, "playlist.json");
    }

    // TMDB API Key
    public void saveTMDBApiKey(String apiKey) {
        preferences.edit().putString(KEY_TMDB_API_KEY, apiKey).apply();
    }

    public String getTMDBApiKey() {
        return preferences.getString(KEY_TMDB_API_KEY, "");
    }

    // Enhanced Import/Export
    public String exportToJson() {
        try {
            // Create the exact JSON structure matching the GitHub repository
            JsonObject exportData = new JsonObject();
            JsonArray categoriesArray = new JsonArray();
            
            // Live TV category
            List<ContentItem> liveTv = getContentItemsByType("Live TV");
            if (!liveTv.isEmpty()) {
                JsonObject liveTvCategory = new JsonObject();
                liveTvCategory.addProperty("MainCategory", "Live TV");
                liveTvCategory.add("SubCategories", gson.toJsonTree(getSubCategories(liveTv)));
                liveTvCategory.add("Entries", gson.toJsonTree(convertToEntries(liveTv)));
                categoriesArray.add(liveTvCategory);
            }
            
            // Movies category
            List<ContentItem> movies = getContentItemsByType("Movie");
            if (!movies.isEmpty()) {
                JsonObject moviesCategory = new JsonObject();
                moviesCategory.addProperty("MainCategory", "Movies");
                moviesCategory.add("SubCategories", gson.toJsonTree(getSubCategories(movies)));
                moviesCategory.add("Entries", gson.toJsonTree(convertToEntries(movies)));
                categoriesArray.add(moviesCategory);
            }
            
            // TV Series category
            List<ContentItem> series = getContentItemsByType("TV Series");
            if (!series.isEmpty()) {
                JsonObject seriesCategory = new JsonObject();
                seriesCategory.addProperty("MainCategory", "TV Series");
                seriesCategory.add("SubCategories", gson.toJsonTree(getSubCategories(series)));
                seriesCategory.add("Entries", gson.toJsonTree(convertToTVSeriesEntries(series)));
                categoriesArray.add(seriesCategory);
            }
            
            exportData.add("Categories", categoriesArray);
            return gson.toJson(exportData);
        } catch (Exception e) {
            Log.e(TAG, "Error exporting data to JSON", e);
            return null;
        }
    }
    
    /**
     * Export data to SQLite database file
     */
    public File exportToSQLite() {
        try {
            SQLiteExporter exporter = new SQLiteExporter(context);
            return exporter.exportToSQLite(contentItems, serverConfigs);
        } catch (Exception e) {
            Log.e(TAG, "Error exporting data to SQLite", e);
            return null;
        }
    }
    
    /**
     * Generate SQLite database data as byte array (for direct upload)
     */
    public byte[] generateSQLiteData() {
        try {
            SQLiteExporter exporter = new SQLiteExporter(context);
            return exporter.generateSQLiteData(contentItems, serverConfigs);
        } catch (Exception e) {
            Log.e(TAG, "Error generating SQLite data", e);
            return null;
        }
    }
    
    /**
     * Save SQLite database to downloads folder
     */
    public boolean saveSQLiteToDownloads() {
        try {
            File dbFile = exportToSQLite();
            if (dbFile != null) {
                SQLiteExporter exporter = new SQLiteExporter(context);
                return exporter.saveToDownloads(dbFile);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error saving SQLite to downloads", e);
            return false;
        }
    }

    public boolean importFromJson(String json) {
        try {
            JsonObject jsonData = JsonParser.parseString(json).getAsJsonObject();
            List<ContentItem> importedItems = new ArrayList<>();
            
            if (jsonData.has("Categories")) {
                JsonArray categories = jsonData.getAsJsonArray("Categories");
                
                // Import from each category
                for (JsonElement categoryElement : categories) {
                    JsonObject category = categoryElement.getAsJsonObject();
                    String mainCategory = category.get("MainCategory").getAsString();
                    
                    if (category.has("Entries")) {
                        JsonArray entries = category.getAsJsonArray("Entries");
                        
                        for (JsonElement entryElement : entries) {
                            JsonObject entry = entryElement.getAsJsonObject();
                            
                            // Handle TV Series with Seasons structure
                            if ("TV Series".equals(mainCategory) && entry.has("Seasons")) {
                                List<ContentItem> seriesItems = parseSeriesWithSeasons(entry, mainCategory);
                                importedItems.addAll(seriesItems);
                            } else {
                                // Handle regular content (Movies, Live TV, or simple TV Series)
                                ContentItem item = parseEntryToContentItem(entry, mainCategory);
                                if (item != null) {
                                    importedItems.add(item);
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback to old format
                Type listType = new TypeToken<List<ContentItem>>(){}.getType();
                importedItems = gson.fromJson(json, listType);
            }
            
            if (importedItems != null && !importedItems.isEmpty()) {
                Log.d(TAG, "Successfully imported " + importedItems.size() + " items");
                contentItems.addAll(importedItems);
                saveData();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing data from JSON", e);
        }
        return false;
    }
    
    private List<ContentItem> parseSeriesWithSeasons(JsonObject seriesEntry, String mainCategory) {
        List<ContentItem> episodes = new ArrayList<>();
        
        try {
            String seriesTitle = getStringValue(seriesEntry, "Title");
            String seriesSubcategory = getStringValue(seriesEntry, "SubCategory");
            String seriesCountry = getStringValue(seriesEntry, "Country");
            String seriesDescription = getStringValue(seriesEntry, "Description");
            String seriesPoster = getStringValue(seriesEntry, "Poster");
            Double seriesRating = getDoubleValue(seriesEntry, "Rating");
            Integer seriesYear = getIntValue(seriesEntry, "Year");
            
            // Log unknown series-level fields
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                logUnknownSeriesFields(seriesEntry, seriesTitle);
            }
            
            if (seriesEntry.has("Seasons")) {
                JsonArray seasons = seriesEntry.getAsJsonArray("Seasons");
                
                for (JsonElement seasonElement : seasons) {
                    try {
                        JsonObject season = seasonElement.getAsJsonObject();
                        Integer seasonNumber = getIntValue(season, "Season");
                        String seasonPoster = getStringValue(season, "SeasonPoster");
                        
                        // Log unknown season-level fields
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            logUnknownSeasonFields(season, seriesTitle, seasonNumber);
                        }
                        
                        if (season.has("Episodes")) {
                            JsonArray episodesArray = season.getAsJsonArray("Episodes");
                            
                            for (JsonElement episodeElement : episodesArray) {
                                try {
                                    JsonObject episode = episodeElement.getAsJsonObject();
                                    
                                    ContentItem episodeItem = new ContentItem();
                                    String episodeTitle = getStringValue(episode, "Title");
                                    Integer episodeNumber = getIntValue(episode, "Episode");
                                    
                                    // Create a clean episode title that follows the desired structure
                                    String episodeDisplayTitle;
                                    if (episodeTitle != null && !episodeTitle.trim().isEmpty()) {
                                        // Use the episode title as provided, but ensure it's clean
                                        episodeDisplayTitle = episodeTitle.trim();
                                    } else {
                                        // No episode title provided, create a default one
                                        if (episodeNumber != null && seasonNumber != null) {
                                            episodeDisplayTitle = "Episode " + episodeNumber;
                                        } else {
                                            episodeDisplayTitle = "Episode";
                                        }
                                    }
                                    
                                    // Set the series title separately for proper grouping
                                    episodeItem.setSeriesTitle(seriesTitle);
                                    episodeItem.setTitle(episodeDisplayTitle);
                                    episodeItem.setType(mainCategory);
                                    episodeItem.setSubcategory(seriesSubcategory);
                                    episodeItem.setCountry(seriesCountry);
                                    episodeItem.setDescription(getStringValue(episode, "Description"));
                                    episodeItem.setImageUrl(!getStringValue(episode, "Thumbnail").isEmpty() ? 
                                                          getStringValue(episode, "Thumbnail") : 
                                                          (!seasonPoster.isEmpty() ? seasonPoster : seriesPoster));
                                    episodeItem.setRating(seriesRating);
                                    episodeItem.setDuration(getStringValue(episode, "Duration"));
                                    episodeItem.setYear(seriesYear);
                                    episodeItem.setSeason(seasonNumber);
                                    episodeItem.setEpisode(episodeNumber);
                                    
                                    // Debug: Log what episode title is being created
                                    Log.d(TAG, "Creating episode: '" + episodeDisplayTitle + "' for series: '" + seriesTitle + "' S" + seasonNumber + "E" + episodeNumber);
                                    
                                    // Parse episode servers safely
                                    if (episode.has("Servers")) {
                                        JsonArray episodeServers = episode.getAsJsonArray("Servers");
                                        List<String> serverList = new ArrayList<>();
                                        
                                        for (JsonElement serverElement : episodeServers) {
                                            try {
                                                JsonObject server = serverElement.getAsJsonObject();
                                                String name = getStringValue(server, "name");
                                                String url = getStringValue(server, "url");
                                                
                                                if (!name.isEmpty() && !url.isEmpty()) {
                                                    // Handle DRM safely
                                                    boolean isDrm = getBooleanValue(server, "drm") != null && getBooleanValue(server, "drm");
                                                    String license = getStringValue(server, "license");
                                                    
                                                    // Log unknown server fields
                                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                                        logUnknownServerFields(server, name);
                                                    }
                                                    
                                                    StringBuilder serverString = new StringBuilder();
                                                    serverString.append(name).append("|").append(url);
                                                    
                                                    if (isDrm && !license.isEmpty()) {
                                                        serverString.append("|drm:").append(license);
                                                    } else if (isDrm) {
                                                        serverString.append("|drm:true");
                                                    }
                                                    
                                                    serverList.add(serverString.toString());
                                                }
                                            } catch (Exception e) {
                                                Log.d(TAG, "Skipping malformed episode server: " + e.getMessage());
                                            }
                                        }
                                        episodeItem.setServers(serverList);
                                    }
                                    
                                    // Log unknown episode-level fields
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        logUnknownEpisodeFields(episode, seriesTitle, seasonNumber, episodeNumber);
                                    }
                                    
                                    episodes.add(episodeItem);
                                    
                                } catch (Exception e) {
                                    Log.d(TAG, "Skipping malformed episode: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Skipping malformed season: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing series with seasons: " + e.getMessage());
        }
        
        return episodes;
    }

    public boolean saveToFile(String filename, String content) {
        try {
            File file = new File(context.getFilesDir(), filename);
            file.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving file: " + filename, e);
            return false;
        }
    }

    public String loadFromFile(String filename) {
        try {
            File file = new File(context.getFilesDir(), filename);
            if (!file.exists()) {
                return null;
            }
            
            try (FileReader reader = new FileReader(file)) {
                StringBuilder content = new StringBuilder();
                char[] buffer = new char[1024];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, bytesRead);
                }
                return content.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading file: " + filename, e);
            return null;
        }
    }

    /**
     * Save a file into the user's Downloads folder.
     * Uses MediaStore on Android 10+; falls back to Environment.DIRECTORY_DOWNLOADS on legacy devices.
     */
    public boolean saveToDownloads(String fileName, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri itemUri = context.getContentResolver().insert(collection, values);
                if (itemUri == null) {
                    Log.e(TAG, "Failed to create download item");
                    return false;
                }

                try (OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                    if (out == null) {
                        Log.e(TAG, "OutputStream is null for " + itemUri);
                        return false;
                    }
                    out.write(content.getBytes());
                }

                // Mark as not pending so it becomes visible to user
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(itemUri, values, null, null);
                return true;
            } else {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
                    File outFile = new File(downloads, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(content.getBytes());
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Downloads", e);
        }
        return false;
    }

    // Storage Management
    public long getStorageSize() {
        try {
            File dataDir = context.getFilesDir();
            return calculateDirectorySize(dataDir);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating storage size", e);
            return 0;
        }
    }

    public long getAvailableSpace() {
        try {
            File dataDir = context.getFilesDir();
            return dataDir.getFreeSpace();
        } catch (Exception e) {
            Log.e(TAG, "Error getting available space", e);
            return 0;
        }
    }

    public boolean migrateStorage() {
        try {
            // This would implement storage migration logic
            // For now, just return success
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error migrating storage", e);
            return false;
        }
    }

    public long optimizeStorage() {
        try {
            long originalSize = getStorageSize();
            
            // Remove duplicate entries
            removeDuplicates();
            
            // Clean up old backup files
            cleanupOldBackups();
            
            long newSize = getStorageSize();
            return originalSize - newSize;
        } catch (Exception e) {
            Log.e(TAG, "Error optimizing storage", e);
            return 0;
        }
    }

    private void cleanupOldBackups() {
        try {
            File dataDir = context.getFilesDir();
            File[] files = dataDir.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".json"));
            
            if (files != null && files.length > 5) {
                // Keep only the 5 most recent backups
                java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                
                for (int i = 5; i < files.length; i++) {
                    files[i].delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old backups", e);
        }
    }

    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    // Helper methods for JSON structure
    private List<String> getSubCategories(List<ContentItem> items) {
        Set<String> subCategories = new HashSet<>();
        for (ContentItem item : items) {
            if (item.getSubcategory() != null && !item.getSubcategory().isEmpty()) {
                subCategories.add(item.getSubcategory());
            }
        }
        return new ArrayList<>(subCategories);
    }

    private List<JsonObject> convertToEntries(List<ContentItem> items) {
        List<JsonObject> entries = new ArrayList<>();
        for (ContentItem item : items) {
            JsonObject entry = new JsonObject();
            entry.addProperty("Title", item.getTitle());
            entry.addProperty("SubCategory", item.getSubcategory() != null ? item.getSubcategory() : "");
            entry.addProperty("Country", item.getCountry() != null ? item.getCountry() : "");
            entry.addProperty("Description", item.getDescription() != null ? item.getDescription() : "");
            entry.addProperty("Poster", item.getImageUrl() != null ? item.getImageUrl() : "");
            entry.addProperty("Thumbnail", item.getImageUrl() != null ? item.getImageUrl() : "");
            entry.addProperty("Rating", item.getRating() != null ? item.getRating() : 0.0);
            entry.addProperty("Duration", item.getDuration() != null ? item.getDuration() : "");
            entry.addProperty("Year", item.getYear() != null ? item.getYear() : 0);
            
            // Convert servers to proper format matching GitHub structure
            if (item.getServers() != null && !item.getServers().isEmpty()) {
                JsonArray servers = new JsonArray();
                for (String server : item.getServers()) {
                    String[] parts = server.split("\\|", 3); // Split into max 3 parts: name, url, drm_info
                    JsonObject serverObj = new JsonObject();
                    serverObj.addProperty("name", parts.length > 0 ? parts[0] : "Unknown");
                    serverObj.addProperty("url", parts.length > 1 ? parts[1] : "");
                    
                    // Handle DRM and license information
                    if (parts.length > 2 && parts[2].startsWith("drm:")) {
                        String drmInfo = parts[2].substring(4); // Remove "drm:" prefix
                        serverObj.addProperty("drm", true);
                        
                        // Check if it's just "true" or contains license information
                        if (!drmInfo.equals("true")) {
                            serverObj.addProperty("license", drmInfo);
                        }
                    } else {
                        // Check for DRM indicators in URL
                        if (parts.length > 1 && (parts[1].contains("license=") || parts[1].contains(".mpd"))) {
                            serverObj.addProperty("drm", true);
                            // Extract license if present in URL
                            String license = extractLicenseFromUrl(parts[1]);
                            if (license != null) {
                                serverObj.addProperty("license", license);
                            }
                        }
                    }
                    
                    servers.add(serverObj);
                }
                entry.add("Servers", servers);
            }
            
            entries.add(entry);
        }
        return entries;
    }

    private List<JsonObject> convertToTVSeriesEntries(List<ContentItem> items) {
        List<JsonObject> entries = new ArrayList<>();
        
        // Group items by series title (use the dedicated series title field)
        Map<String, List<ContentItem>> seriesGroups = new HashMap<>();
        for (ContentItem item : items) {
            String seriesTitle = item.getSeriesTitle();
            if (seriesTitle == null || seriesTitle.trim().isEmpty()) {
                // Fallback to extracting from title for backward compatibility
                seriesTitle = extractSeriesTitle(item.getTitle());
            }
            seriesGroups.computeIfAbsent(seriesTitle, k -> new ArrayList<>()).add(item);
        }
        
        for (Map.Entry<String, List<ContentItem>> seriesEntryKV : seriesGroups.entrySet()) {
            String seriesTitle = seriesEntryKV.getKey();
            List<ContentItem> seriesItems = seriesEntryKV.getValue();
            if (seriesItems.isEmpty()) continue;
            
            // Group by season
            Map<Integer, List<ContentItem>> seasonMap = new HashMap<>();
            for (ContentItem item : seriesItems) {
                int seasonNumber = item.getSeason() != null ? item.getSeason() : extractSeasonNumber(item.getTitle());
                seasonMap.computeIfAbsent(seasonNumber, k -> new ArrayList<>()).add(item);
            }
            
            // Build series object
            ContentItem first = seriesItems.get(0);
            JsonObject seriesObj = new JsonObject();
            seriesObj.addProperty("Title", seriesTitle);
            seriesObj.addProperty("SubCategory", first.getSubcategory() != null ? first.getSubcategory() : "");
            seriesObj.addProperty("Country", first.getCountry() != null ? first.getCountry() : "");
            seriesObj.addProperty("Description", first.getDescription() != null ? first.getDescription() : "");
            seriesObj.addProperty("Poster", first.getImageUrl() != null ? first.getImageUrl() : "");
            seriesObj.addProperty("Thumbnail", first.getImageUrl() != null ? first.getImageUrl() : "");
            seriesObj.addProperty("Rating", first.getRating() != null ? first.getRating() : 0.0);
            seriesObj.addProperty("Year", first.getYear() != null ? first.getYear() : 0);
            
            // Seasons
            JsonArray seasons = new JsonArray();
            List<Integer> seasonNumbers = new ArrayList<>(seasonMap.keySet());
            java.util.Collections.sort(seasonNumbers);
            for (Integer seasonNumber : seasonNumbers) {
                List<ContentItem> eps = seasonMap.get(seasonNumber);
                if (eps == null || eps.isEmpty()) continue;
                
                // Sort episodes
                eps.sort((a, b) -> Integer.compare(
                        b.getEpisode() == null ? extractEpisodeNumber(b.getTitle()) : b.getEpisode(),
                        a.getEpisode() == null ? extractEpisodeNumber(a.getTitle()) : a.getEpisode()
                ));
                java.util.Collections.reverse(eps);
                
                JsonObject seasonObj = new JsonObject();
                seasonObj.addProperty("Season", seasonNumber);
                seasonObj.addProperty("SeasonPoster", eps.get(0).getImageUrl() != null ? eps.get(0).getImageUrl() : (first.getImageUrl() != null ? first.getImageUrl() : ""));
                
                JsonArray episodes = new JsonArray();
                for (ContentItem epItem : eps) {
                    JsonObject epObj = new JsonObject();
                    int epNum = epItem.getEpisode() != null ? epItem.getEpisode() : extractEpisodeNumber(epItem.getTitle());
                    epObj.addProperty("Episode", epNum);
                    epObj.addProperty("Title", epItem.getTitle());
                    epObj.addProperty("Duration", epItem.getDuration() != null ? epItem.getDuration() : "");
                    epObj.addProperty("Description", epItem.getDescription() != null ? epItem.getDescription() : "");
                    epObj.addProperty("Thumbnail", epItem.getImageUrl() != null ? epItem.getImageUrl() : "");
                    
                    // Servers
                    if (epItem.getServers() != null && !epItem.getServers().isEmpty()) {
                        JsonArray servers = new JsonArray();
                        for (String server : epItem.getServers()) {
                            String[] parts = server.split("\\|", 3);
                            JsonObject serverObj = new JsonObject();
                            serverObj.addProperty("name", parts.length > 0 ? parts[0] : "Unknown");
                            serverObj.addProperty("url", parts.length > 1 ? parts[1] : "");
                            if (parts.length > 2 && parts[2].startsWith("drm:")) {
                                String drmInfo = parts[2].substring(4);
                                serverObj.addProperty("drm", true);
                                if (!drmInfo.equals("true")) {
                                    serverObj.addProperty("license", drmInfo);
                                }
                            }
                            servers.add(serverObj);
                        }
                        epObj.add("Servers", servers);
                    }
                    
                    episodes.add(epObj);
                }
                
                seasonObj.add("Episodes", episodes);
                seasons.add(seasonObj);
            }
            
            seriesObj.add("Seasons", seasons);
            entries.add(seriesObj);
        }
        
        return entries;
    }
    
    private int extractEpisodeNumber(String title) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("E(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private ContentItem parseEntryToContentItem(JsonObject entry, String mainCategory) {
        try {
            ContentItem item = new ContentItem();
            
            // Parse known fields safely
            item.setTitle(getStringValue(entry, "Title"));
            item.setSubcategory(getStringValue(entry, "SubCategory"));
            item.setCountry(getStringValue(entry, "Country"));
            item.setDescription(getStringValue(entry, "Description"));
            item.setImageUrl(getStringValue(entry, "Poster"));
            item.setRating(getDoubleValue(entry, "Rating"));
            item.setDuration(getStringValue(entry, "Duration"));
            item.setYear(getIntValue(entry, "Year"));
            item.setType(mainCategory);
            
            // Log unknown fields for future development (optional - only in debug)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                logUnknownContentFields(entry, item.getTitle());
            }
            
            // Convert servers from JSON format to string format, handling DRM and license fields
            if (entry.has("Servers")) {
                JsonArray servers = entry.getAsJsonArray("Servers");
                List<String> serverList = new ArrayList<>();
                for (JsonElement serverElement : servers) {
                    try {
                        JsonObject server = serverElement.getAsJsonObject();
                        String name = getStringValue(server, "name");
                        String url = getStringValue(server, "url");
                        
                        // Skip servers with empty name or url
                        if (name.isEmpty() || url.isEmpty()) {
                            Log.d(TAG, "Skipping server with missing name or url");
                            continue;
                        }
                        
                        // Check if this is a DRM server (safely)
                        boolean isDrm = getBooleanValue(server, "drm") != null && getBooleanValue(server, "drm");
                        String license = getStringValue(server, "license");
                        
                        // Log any unknown fields for future reference (optional)
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            logUnknownServerFields(server, name);
                        }
                        
                        // Build server string with DRM information if present
                        StringBuilder serverString = new StringBuilder();
                        serverString.append(name).append("|").append(url);
                        
                        // Add DRM information if present
                        if (isDrm && license != null && !license.isEmpty()) {
                            serverString.append("|drm:").append(license);
                        } else if (isDrm) {
                            serverString.append("|drm:true");
                        }
                        
                        serverList.add(serverString.toString());
                    } catch (Exception e) {
                        Log.d(TAG, "Skipping malformed server entry: " + e.getMessage());
                        // Continue processing other servers
                    }
                }
                item.setServers(serverList);
            }
            
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Error converting entry to ContentItem", e);
            return null;
        }
    }

    private String extractSeriesTitle(String episodeTitle) {
        if (episodeTitle == null || episodeTitle.trim().isEmpty()) {
            return "Unknown Series";
        }
        
        String title = episodeTitle.trim();
        
        // Simple and reliable approach: remove common season/episode patterns
        // Pattern 1: Remove "S##E##" format (most common)
        title = title.replaceAll("\\s+S\\d+E\\d+.*$", "");
        
        // Pattern 2: Remove "Season # Episode #" format  
        title = title.replaceAll("\\s+Season\\s+\\d+.*$", "");
        
        // Pattern 3: Remove " - Episode Title" if it looks like episode info
        if (title.matches(".*\\s+-\\s+.*")) {
            String[] parts = title.split("\\s+-\\s+", 2);
            if (parts.length > 1) {
                // Only keep the first part if it looks like a series name
                String firstPart = parts[0].trim();
                if (firstPart.length() > 3) { // Basic length check
                    title = firstPart;
                }
            }
        }
        
        // Pattern 4: Remove "Episode #" or "Ep #"
        title = title.replaceAll("\\s+(Episode|Ep)\\s+\\d+.*$", "");
        
        // Pattern 5: Remove "#x##" format (like 1x01)
        title = title.replaceAll("\\s+\\d+x\\d+.*$", "");
        
        return title.trim();
    }

    private int extractSeasonNumber(String title) {
        // Extract season number from title (e.g., "Game of Thrones S01" -> 1)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("S(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1; // Default to season 1
    }

    private String extractLicenseFromUrl(String url) {
        // Extract license from URL if present
        if (url.contains("license=")) {
            int start = url.indexOf("license=") + 8;
            int end = url.indexOf("&", start);
            if (end == -1) end = url.length();
            return url.substring(start, end);
        }
        return null;
    }

    private String getStringValue(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return "";
            }
            // Handle different JSON types safely
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            } else if (element.isJsonArray() || element.isJsonObject()) {
                // Skip arrays and objects silently - they're for future use
                Log.d(TAG, "Skipping non-primitive field '" + key + "' (array/object) - future use");
                return "";
            }
            return "";
        } catch (Exception e) {
            Log.d(TAG, "Safely ignoring field '" + key + "': " + e.getMessage());
            return "";
        }
    }

    private Integer getIntValue(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            // Only process primitive numbers
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }
            // Skip non-numeric fields silently
            if (!element.isJsonPrimitive()) {
                Log.d(TAG, "Skipping non-primitive field '" + key + "' for integer conversion");
            }
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Safely ignoring field '" + key + "' for integer conversion: " + e.getMessage());
            return null;
        }
    }

    private Double getDoubleValue(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            // Only process primitive numbers
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble();
            }
            // Skip non-numeric fields silently
            if (!element.isJsonPrimitive()) {
                Log.d(TAG, "Skipping non-primitive field '" + key + "' for double conversion");
            }
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Safely ignoring field '" + key + "' for double conversion: " + e.getMessage());
            return null;
        }
    }

    private Boolean getBooleanValue(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            // Only process primitive booleans
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            // Try to parse string representations of booleans
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().toLowerCase();
                if ("true".equals(value) || "1".equals(value)) {
                    return true;
                } else if ("false".equals(value) || "0".equals(value)) {
                    return false;
                }
            }
            // Skip non-boolean fields silently
            if (!element.isJsonPrimitive()) {
                Log.d(TAG, "Skipping non-primitive field '" + key + "' for boolean conversion");
            }
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Safely ignoring field '" + key + "' for boolean conversion: " + e.getMessage());
            return null;
        }
    }

    // Data persistence
    private void loadData() {
        loadContentItems();
        loadServerConfigs();
    }

    private void saveData() {
        saveContentItems();
        saveServerConfigs();
    }

    private void loadContentItems() {
        try {
            String json = preferences.getString(KEY_CONTENT_ITEMS, "[]");
            Type listType = new TypeToken<List<ContentItem>>(){}.getType();
            contentItems = gson.fromJson(json, listType);
            if (contentItems == null) {
                contentItems = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading content items", e);
            contentItems = new ArrayList<>();
        }
    }

    private void saveContentItems() {
        try {
            String json = gson.toJson(contentItems);
            preferences.edit().putString(KEY_CONTENT_ITEMS, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving content items", e);
        }
    }

    private void loadServerConfigs() {
        try {
            String json = preferences.getString(KEY_SERVER_CONFIGS, "[]");
            Type listType = new TypeToken<List<ServerConfig>>(){}.getType();
            serverConfigs = gson.fromJson(json, listType);
            if (serverConfigs == null) {
                serverConfigs = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading server configs", e);
            serverConfigs = new ArrayList<>();
        }
    }

    private void saveServerConfigs() {
        try {
            String json = gson.toJson(serverConfigs);
            preferences.edit().putString(KEY_SERVER_CONFIGS, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving server configs", e);
        }
    }

    // Test method to verify DRM handling
    public boolean testDrmHandling() {
        try {
            // Create a test item with DRM servers
            ContentItem testItem = new ContentItem();
            testItem.setTitle("Test DRM Content");
            testItem.setType("Live TV");
            testItem.setSubcategory("Test");
            testItem.setCountry("Test Country");
            testItem.setDescription("Test description");
            testItem.setImageUrl("https://test.com/poster.jpg");
            testItem.setRating(5.0);
            testItem.setDuration("1:00");
            testItem.setYear(2024);
            
            // Add DRM servers
            List<String> drmServers = new ArrayList<>();
            drmServers.add("HD|https://test.com/stream.mpd|drm:testlicense123");
            drmServers.add("720p|https://test.com/stream.m3u8");
            testItem.setServers(drmServers);
            
            // Export to JSON
            String json = exportToJson();
            if (json == null) {
                Log.e(TAG, "Export failed");
                return false;
            }
            
            // Import back
            boolean importSuccess = importFromJson(json);
            if (!importSuccess) {
                Log.e(TAG, "Import failed");
                return false;
            }
            
            // Verify DRM servers are preserved
            List<ContentItem> importedItems = getAllContent();
            for (ContentItem item : importedItems) {
                if (item.getTitle().equals("Test DRM Content")) {
                    List<String> servers = item.getServers();
                    if (servers != null) {
                        for (String server : servers) {
                            if (server.contains("|drm:")) {
                                Log.i(TAG, "DRM server preserved: " + server);
                                return true;
                            }
                        }
                    }
                }
            }
            
            Log.e(TAG, "DRM servers not preserved");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "DRM handling test failed", e);
            return false;
        }
    }

    // Test method to verify TV Series import structure
    public boolean testTVSeriesImportStructure() {
        try {
            // Create a test TV series with proper structure
            String testJson = "{\n" +
                "  \"Categories\": [\n" +
                "    {\n" +
                "      \"MainCategory\": \"TV Series\",\n" +
                "      \"SubCategories\": [\"Test\"],\n" +
                "      \"Entries\": [\n" +
                "        {\n" +
                "          \"Title\": \"Wednesday\",\n" +
                "          \"SubCategory\": \"Drama\",\n" +
                "          \"Country\": \"US\",\n" +
                "          \"Description\": \"Test series description\",\n" +
                "          \"Poster\": \"https://test.com/poster.jpg\",\n" +
                "          \"Rating\": 8.5,\n" +
                "          \"Year\": 2022,\n" +
                "          \"Seasons\": [\n" +
                "            {\n" +
                "              \"Season\": 1,\n" +
                "              \"SeasonPoster\": \"https://test.com/season1.jpg\",\n" +
                "              \"Episodes\": [\n" +
                "                {\n" +
                "                  \"Episode\": 1,\n" +
                "                  \"Title\": \"Wednesday's First Day\",\n" +
                "                  \"Duration\": \"45:00\",\n" +
                "                  \"Description\": \"Episode 1 description\",\n" +
                "                  \"Thumbnail\": \"https://test.com/ep1.jpg\",\n" +
                "                  \"Servers\": [\n" +
                "                    {\n" +
                "                      \"name\": \"VidSrc\",\n" +
                "                      \"url\": \"https://vidsrc.to/embed/wednesday\"\n" +
                "                    }\n" +
                "                  ]\n" +
                "                },\n" +
                "                {\n" +
                "                  \"Episode\": 2,\n" +
                "                  \"Title\": \"Wednesday's Second Day\",\n" +
                "                  \"Duration\": \"45:00\",\n" +
                "                  \"Description\": \"Episode 2 description\",\n" +
                "                  \"Thumbnail\": \"https://test.com/ep2.jpg\",\n" +
                "                  \"Servers\": [\n" +
                "                    {\n" +
                "                      \"name\": \"VidSrc\",\n" +
                "                      \"url\": \"https://vidsrc.to/embed/wednesday\"\n" +
                "                    }\n" +
                "                  ]\n" +
                "                }\n" +
                "              ]\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
            
            // Import the test JSON
            boolean importSuccess = importFromJson(testJson);
            if (!importSuccess) {
                Log.e(TAG, "TV Series import test failed");
                return false;
            }
            
            // Verify the structure
            List<ContentItem> importedItems = getAllContent();
            boolean foundSeries = false;
            boolean foundEpisodes = false;
            
            for (ContentItem item : importedItems) {
                if ("Wednesday".equals(item.getSeriesTitle()) && "TV Series".equals(item.getType())) {
                    foundSeries = true;
                    if (item.getSeason() != null && item.getEpisode() != null) {
                        foundEpisodes = true;
                        Log.i(TAG, "Found episode: " + item.getTitle() + " for series: " + item.getSeriesTitle() + 
                              " S" + item.getSeason() + "E" + item.getEpisode());
                    }
                }
            }
            
            if (foundSeries && foundEpisodes) {
                Log.i(TAG, "TV Series import structure test passed");
                return true;
            } else {
                Log.e(TAG, "TV Series import structure test failed - series: " + foundSeries + ", episodes: " + foundEpisodes);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "TV Series import structure test failed", e);
            return false;
        }
    }

    private void logUnknownServerFields(JsonObject server, String name) {
        for (Map.Entry<String, JsonElement> entry : server.entrySet()) {
            if (!entry.getKey().equals("name") && !entry.getKey().equals("url") && !entry.getKey().equals("drm") && !entry.getKey().equals("license")) {
                Log.d(TAG, "Found unknown field '" + entry.getKey() + "' in server '" + name + "'");
            }
        }
    }

    private void logUnknownContentFields(JsonObject entry, String title) {
        for (Map.Entry<String, JsonElement> entryKV : entry.entrySet()) {
            if (!entryKV.getKey().equals("Title") && !entryKV.getKey().equals("SubCategory") && !entryKV.getKey().equals("Country") &&
                !entryKV.getKey().equals("Description") && !entryKV.getKey().equals("Poster") && !entryKV.getKey().equals("Thumbnail") &&
                !entryKV.getKey().equals("Rating") && !entryKV.getKey().equals("Duration") && !entryKV.getKey().equals("Year") &&
                !entryKV.getKey().equals("Servers")) {
                Log.d(TAG, "Found unknown field '" + entryKV.getKey() + "' in entry for title: " + title);
            }
        }
    }

    private void logUnknownSeriesFields(JsonObject seriesEntry, String seriesTitle) {
        for (Map.Entry<String, JsonElement> entry : seriesEntry.entrySet()) {
            if (!entry.getKey().equals("Title") && !entry.getKey().equals("SubCategory") && !entry.getKey().equals("Country") &&
                !entry.getKey().equals("Description") && !entry.getKey().equals("Poster") && !entry.getKey().equals("Rating") &&
                !entry.getKey().equals("Year") && !entry.getKey().equals("Seasons")) {
                Log.d(TAG, "Found unknown field '" + entry.getKey() + "' in series entry for title: " + seriesTitle);
            }
        }
    }

    private void logUnknownSeasonFields(JsonObject seasonEntry, String seriesTitle, Integer seasonNumber) {
        for (Map.Entry<String, JsonElement> entry : seasonEntry.entrySet()) {
            if (!entry.getKey().equals("Season") && !entry.getKey().equals("SeasonPoster") && !entry.getKey().equals("Episodes")) {
                Log.d(TAG, "Found unknown field '" + entry.getKey() + "' in season entry for series: " + seriesTitle + ", season: " + seasonNumber);
            }
        }
    }

    private void logUnknownEpisodeFields(JsonObject episodeEntry, String seriesTitle, Integer seasonNumber, Integer episodeNumber) {
        for (Map.Entry<String, JsonElement> entry : episodeEntry.entrySet()) {
            if (!entry.getKey().equals("Episode") && !entry.getKey().equals("Title") && !entry.getKey().equals("Duration") &&
                !entry.getKey().equals("Description") && !entry.getKey().equals("Thumbnail") && !entry.getKey().equals("Servers")) {
                Log.d(TAG, "Found unknown field '" + entry.getKey() + "' in episode entry for series: " + seriesTitle + ", season: " + seasonNumber + ", episode: " + episodeNumber);
            }
        }
    }
}
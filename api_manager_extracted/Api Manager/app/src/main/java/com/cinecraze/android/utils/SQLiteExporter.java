package com.cinecraze.android.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SQLiteExporter {
    
    private static final String TAG = "SQLiteExporter";
    private static final String DATABASE_NAME = "playlist.db";
    private static final int DATABASE_VERSION = 1;
    
    private final Context context;
    private final Gson gson;
    
    public SQLiteExporter(Context context) {
        this.context = context;
        this.gson = new Gson();
    }
    
    /**
     * Export data to SQLite database file
     */
    public File exportToSQLite(List<ContentItem> contentItems, List<ServerConfig> serverConfigs) {
        try {
            // Create database file
            File dbFile = new File(context.getFilesDir(), DATABASE_NAME);
            if (dbFile.exists()) {
                dbFile.delete();
            }
            
            // Create database helper
            PlaylistDatabaseHelper dbHelper = new PlaylistDatabaseHelper(context);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            try {
                // Export categories
                exportCategories(db, contentItems);
                
                // Export entries
                exportEntries(db, contentItems);
                
                // Export metadata
                exportMetadata(db, contentItems, serverConfigs);
                
                Log.i(TAG, "Successfully exported " + contentItems.size() + " items to SQLite");
                
            } finally {
                db.close();
                dbHelper.close();
            }
            
            return dbFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting to SQLite", e);
            return null;
        }
    }
    
    /**
     * Generate SQLite database data as byte array (for direct upload)
     */
    public byte[] generateSQLiteData(List<ContentItem> contentItems, List<ServerConfig> serverConfigs) {
        try {
            // Create a temporary database file
            File tempDbFile = new File(context.getFilesDir(), "temp_" + DATABASE_NAME);
            if (tempDbFile.exists()) {
                tempDbFile.delete();
            }
            
            // Create database helper with the temp file
            PlaylistDatabaseHelper dbHelper = new PlaylistDatabaseHelper(context, tempDbFile.getAbsolutePath());
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            try {
                // Export categories
                exportCategories(db, contentItems);
                
                // Export entries
                exportEntries(db, contentItems);
                
                // Export metadata
                exportMetadata(db, contentItems, serverConfigs);
                
                Log.i(TAG, "Successfully generated SQLite data for " + contentItems.size() + " items");
                
                // Read the database file into byte array
                if (tempDbFile.exists()) {
                    byte[] data = java.nio.file.Files.readAllBytes(tempDbFile.toPath());
                    Log.i(TAG, "Generated SQLite data size: " + data.length + " bytes");
                    return data;
                } else {
                    Log.e(TAG, "Temporary database file not found after creation");
                    return null;
                }
                
            } finally {
                db.close();
                dbHelper.close();
                
                // Clean up temporary file
                if (tempDbFile.exists()) {
                    tempDbFile.delete();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating SQLite data", e);
            return null;
        }
    }
    
    /**
     * Export categories to database
     */
    private void exportCategories(SQLiteDatabase db, List<ContentItem> contentItems) {
        try {
            // Group by main category and collect sub-categories
            Map<String, Set<String>> categoryMap = new HashMap<>();
            
            for (ContentItem item : contentItems) {
                String mainCategory = item.getType();
                String subCategory = item.getSubcategory();
                
                if (mainCategory != null && !mainCategory.trim().isEmpty()) {
                    categoryMap.computeIfAbsent(mainCategory, k -> new HashSet<>());
                    if (subCategory != null && !subCategory.trim().isEmpty()) {
                        categoryMap.get(mainCategory).add(subCategory);
                    }
                }
            }
            
            // Insert categories
            for (Map.Entry<String, Set<String>> entry : categoryMap.entrySet()) {
                String mainCategory = entry.getKey();
                Set<String> subCategories = entry.getValue();
                
                String subCategoriesJson = gson.toJson(new ArrayList<>(subCategories));
                
                String sql = "INSERT INTO categories (main_category, sub_categories) VALUES (?, ?)";
                db.execSQL(sql, new Object[]{mainCategory, subCategoriesJson});
            }
            
            Log.i(TAG, "Exported " + categoryMap.size() + " categories");
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting categories", e);
        }
    }
    
    /**
     * Export entries to database
     */
    private void exportEntries(SQLiteDatabase db, List<ContentItem> contentItems) {
        try {
            for (ContentItem item : contentItems) {
                // Convert servers to JSON format
                String serversJson = "";
                if (item.getServers() != null && !item.getServers().isEmpty()) {
                    List<JsonObject> serverObjects = new ArrayList<>();
                    for (String server : item.getServers()) {
                        JsonObject serverObj = parseServerString(server);
                        if (serverObj != null) {
                            serverObjects.add(serverObj);
                        }
                    }
                    serversJson = gson.toJson(serverObjects);
                }
                
                // Convert seasons to JSON format (for TV Series)
                String seasonsJson = "";
                if ("TV Series".equals(item.getType()) && item.getSeason() != null) {
                    JsonObject seasonObj = new JsonObject();
                    seasonObj.addProperty("season", item.getSeason());
                    seasonObj.addProperty("episode", item.getEpisode() != null ? item.getEpisode() : 1);
                    seasonObj.addProperty("title", item.getTitle());
                    seasonObj.addProperty("description", item.getDescription() != null ? item.getDescription() : "");
                    seasonObj.addProperty("thumbnail", item.getImageUrl() != null ? item.getImageUrl() : "");
                    seasonObj.addProperty("duration", item.getDuration() != null ? item.getDuration() : "");
                    
                    // Add servers for this episode
                    if (item.getServers() != null && !item.getServers().isEmpty()) {
                        JsonArray episodeServers = new JsonArray();
                        for (String server : item.getServers()) {
                            JsonObject serverObj = parseServerString(server);
                            if (serverObj != null) {
                                episodeServers.add(serverObj);
                            }
                        }
                        seasonObj.add("servers", episodeServers);
                    }
                    
                    seasonsJson = gson.toJson(seasonObj);
                }
                
                // Insert entry
                String sql = "INSERT INTO entries (title, sub_category, country, description, poster, " +
                           "thumbnail, rating, duration, year, main_category, servers_json, seasons_json, related_json) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                db.execSQL(sql, new Object[]{
                    item.getTitle() != null ? item.getTitle() : "",
                    item.getSubcategory() != null ? item.getSubcategory() : "",
                    item.getCountry() != null ? item.getCountry() : "",
                    item.getDescription() != null ? item.getDescription() : "",
                    item.getImageUrl() != null ? item.getImageUrl() : "",
                    item.getImageUrl() != null ? item.getImageUrl() : "", // thumbnail same as poster
                    item.getRating() != null ? item.getRating().toString() : "0.0",
                    item.getDuration() != null ? item.getDuration() : "",
                    item.getYear() != null ? item.getYear().toString() : "0",
                    item.getType() != null ? item.getType() : "",
                    serversJson,
                    seasonsJson,
                    "" // related_json (empty for now)
                });
            }
            
            Log.i(TAG, "Exported " + contentItems.size() + " entries");
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting entries", e);
        }
    }
    
    /**
     * Export metadata to database
     */
    private void exportMetadata(SQLiteDatabase db, List<ContentItem> contentItems, List<ServerConfig> serverConfigs) {
        try {
            String sql = "INSERT INTO metadata (last_updated, source_url, total_entries, version) VALUES (?, ?, ?, ?)";
            
            String lastUpdated = java.time.LocalDateTime.now().toString();
            String sourceUrl = "Generated by Api Manager Android App";
            int totalEntries = contentItems.size();
            String version = "1.0";
            
            db.execSQL(sql, new Object[]{lastUpdated, sourceUrl, totalEntries, version});
            
            Log.i(TAG, "Exported metadata");
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting metadata", e);
        }
    }
    
    /**
     * Parse server string to JSON object
     */
    private JsonObject parseServerString(String server) {
        try {
            String[] parts = server.split("\\|", 3);
            if (parts.length < 2) {
                return null;
            }
            
            JsonObject serverObj = new JsonObject();
            serverObj.addProperty("name", parts[0]);
            serverObj.addProperty("url", parts[1]);
            
            // Handle DRM information
            if (parts.length > 2 && parts[2].startsWith("drm:")) {
                String drmInfo = parts[2].substring(4);
                serverObj.addProperty("drm", true);
                
                if (!drmInfo.equals("true")) {
                    serverObj.addProperty("license", drmInfo);
                }
            } else {
                serverObj.addProperty("drm", false);
            }
            
            return serverObj;
            
        } catch (Exception e) {
            Log.w(TAG, "Error parsing server string: " + server, e);
            return null;
        }
    }
    
    /**
     * Save database data to downloads folder
     */
    public boolean saveToDownloads(byte[] dbData) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, DATABASE_NAME);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/x-sqlite3");
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

                android.net.Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                android.net.Uri itemUri = context.getContentResolver().insert(collection, values);
                if (itemUri == null) {
                    Log.e(TAG, "Failed to create download item");
                    return false;
                }

                try (java.io.OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                    if (out == null) {
                        Log.e(TAG, "OutputStream is null for " + itemUri);
                        return false;
                    }
                    
                    // Write database data directly to output stream
                    out.write(dbData);
                }

                // Mark as not pending so it becomes visible to user
                values.clear();
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(itemUri, values, null, null);
                Log.i(TAG, "Successfully saved SQLite database to Downloads: " + DATABASE_NAME);
                return true;
            } else {
                File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
                    File outFile = new File(downloads, DATABASE_NAME);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        // Write database data directly
                        fos.write(dbData);
                    }
                    Log.i(TAG, "Successfully saved SQLite database to Downloads: " + DATABASE_NAME);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Downloads", e);
        }
        return false;
    }
    
    /**
     * Save database file to downloads folder (legacy method)
     */
    public boolean saveToDownloads(File dbFile) {
        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(dbFile.toPath());
            return saveToDownloads(fileBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error reading file for download", e);
            return false;
        }
    }
    
    /**
     * Database helper class
     */
    private static class PlaylistDatabaseHelper extends SQLiteOpenHelper {
        
        public PlaylistDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        public PlaylistDatabaseHelper(Context context, String dbPath) {
            super(context, dbPath, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            // Create entries table
            db.execSQL(
                "CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "sub_category TEXT, " +
                "country TEXT, " +
                "description TEXT, " +
                "poster TEXT, " +
                "thumbnail TEXT, " +
                "rating TEXT, " +
                "duration TEXT, " +
                "year TEXT, " +
                "main_category TEXT, " +
                "servers_json TEXT, " +
                "seasons_json TEXT, " +
                "related_json TEXT" +
                ")"
            );
            
            // Create categories table
            db.execSQL(
                "CREATE TABLE categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "main_category TEXT, " +
                "sub_categories TEXT" +
                ")"
            );
            
            // Create metadata table
            db.execSQL(
                "CREATE TABLE metadata (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "last_updated TEXT, " +
                "source_url TEXT, " +
                "total_entries INTEGER, " +
                "version TEXT" +
                ")"
            );
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drop and recreate tables for simplicity
            db.execSQL("DROP TABLE IF EXISTS entries");
            db.execSQL("DROP TABLE IF EXISTS categories");
            db.execSQL("DROP TABLE IF EXISTS metadata");
            onCreate(db);
        }
    }
}
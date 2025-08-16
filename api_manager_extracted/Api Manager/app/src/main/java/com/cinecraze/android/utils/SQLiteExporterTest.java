package com.cinecraze.android.utils;

import android.content.Context;
import android.util.Log;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.ServerConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class for SQLiteExporter functionality
 * This can be used to verify the export works correctly
 */
public class SQLiteExporterTest {
    
    private static final String TAG = "SQLiteExporterTest";
    
    /**
     * Test the SQLite export functionality
     */
    public static boolean testSQLiteExport(Context context) {
        try {
            Log.i(TAG, "Starting SQLite export test...");
            
            // Create test data
            List<ContentItem> testItems = createTestData();
            List<ServerConfig> testConfigs = createTestServerConfigs();
            
            // Create exporter
            SQLiteExporter exporter = new SQLiteExporter(context);
            
            // Export to SQLite
            File dbFile = exporter.exportToSQLite(testItems, testConfigs);
            
            if (dbFile != null && dbFile.exists()) {
                Log.i(TAG, "SQLite export test successful! File size: " + dbFile.length() + " bytes");
                Log.i(TAG, "Database file created at: " + dbFile.getAbsolutePath());
                return true;
            } else {
                Log.e(TAG, "SQLite export test failed - no file created");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "SQLite export test failed with exception", e);
            return false;
        }
    }
    
    /**
     * Create test content items
     */
    private static List<ContentItem> createTestData() {
        List<ContentItem> items = new ArrayList<>();
        
        // Test Live TV item
        ContentItem liveTv = new ContentItem();
        liveTv.setTitle("Test Live TV Channel");
        liveTv.setType("Live TV");
        liveTv.setSubcategory("News");
        liveTv.setCountry("US");
        liveTv.setDescription("A test live TV channel");
        liveTv.setImageUrl("https://example.com/poster.jpg");
        liveTv.setRating(4.5);
        liveTv.setDuration("24:00");
        liveTv.setYear(2024);
        
        List<String> liveTvServers = new ArrayList<>();
        liveTvServers.add("HD|https://example.com/stream.m3u8");
        liveTvServers.add("SD|https://example.com/stream_sd.m3u8");
        liveTv.setServers(liveTvServers);
        
        items.add(liveTv);
        
        // Test Movie item
        ContentItem movie = new ContentItem();
        movie.setTitle("Test Movie");
        movie.setType("Movie");
        movie.setSubcategory("Action");
        movie.setCountry("US");
        movie.setDescription("A test action movie");
        movie.setImageUrl("https://example.com/movie_poster.jpg");
        movie.setRating(8.0);
        movie.setDuration("2:15:30");
        movie.setYear(2023);
        
        List<String> movieServers = new ArrayList<>();
        movieServers.add("1080p|https://example.com/movie_1080p.m3u8");
        movieServers.add("720p|https://example.com/movie_720p.m3u8");
        movie.setServers(movieServers);
        
        items.add(movie);
        
        // Test TV Series item
        ContentItem episode1 = new ContentItem();
        episode1.setTitle("Episode 1");
        episode1.setType("TV Series");
        episode1.setSeriesTitle("Test TV Series");
        episode1.setSubcategory("Drama");
        episode1.setCountry("US");
        episode1.setDescription("First episode of the test series");
        episode1.setImageUrl("https://example.com/series_poster.jpg");
        episode1.setRating(7.5);
        episode1.setDuration("45:00");
        episode1.setYear(2023);
        episode1.setSeason(1);
        episode1.setEpisode(1);
        
        List<String> episode1Servers = new ArrayList<>();
        episode1Servers.add("HD|https://example.com/episode1.m3u8");
        episode1.setServers(episode1Servers);
        
        items.add(episode1);
        
        // Test TV Series episode 2
        ContentItem episode2 = new ContentItem();
        episode2.setTitle("Episode 2");
        episode2.setType("TV Series");
        episode2.setSeriesTitle("Test TV Series");
        episode2.setSubcategory("Drama");
        episode2.setCountry("US");
        episode2.setDescription("Second episode of the test series");
        episode2.setImageUrl("https://example.com/series_poster.jpg");
        episode2.setRating(8.0);
        episode2.setDuration("45:00");
        episode2.setYear(2023);
        episode2.setSeason(1);
        episode2.setEpisode(2);
        
        List<String> episode2Servers = new ArrayList<>();
        episode2Servers.add("HD|https://example.com/episode2.m3u8");
        episode2.setServers(episode2Servers);
        
        items.add(episode2);
        
        // Test DRM content
        ContentItem drmContent = new ContentItem();
        drmContent.setTitle("DRM Protected Content");
        drmContent.setType("Movie");
        drmContent.setSubcategory("Premium");
        drmContent.setCountry("US");
        drmContent.setDescription("DRM protected premium content");
        drmContent.setImageUrl("https://example.com/drm_poster.jpg");
        drmContent.setRating(9.0);
        drmContent.setDuration("1:30:00");
        drmContent.setYear(2024);
        
        List<String> drmServers = new ArrayList<>();
        drmServers.add("4K|https://example.com/drm_stream.mpd|drm:testlicense123");
        drmServers.add("HD|https://example.com/drm_stream_hd.mpd|drm:testlicense456");
        drmContent.setServers(drmServers);
        
        items.add(drmContent);
        
        Log.i(TAG, "Created " + items.size() + " test content items");
        return items;
    }
    
    /**
     * Create test server configurations
     */
    private static List<ServerConfig> createTestServerConfigs() {
        List<ServerConfig> configs = new ArrayList<>();
        
        ServerConfig config1 = new ServerConfig();
        config1.setName("Test Server 1");
        config1.setUrl("https://test1.example.com");
        config1.setEnabled(true);
        configs.add(config1);
        
        ServerConfig config2 = new ServerConfig();
        config2.setName("Test Server 2");
        config2.setUrl("https://test2.example.com");
        config2.setEnabled(false);
        configs.add(config2);
        
        Log.i(TAG, "Created " + configs.size() + " test server configs");
        return configs;
    }
    
    /**
     * Verify the exported database structure
     */
    public static boolean verifyDatabaseStructure(File dbFile) {
        try {
            // This would verify the database structure matches the expected schema
            // For now, just check if the file exists and has content
            if (dbFile.exists() && dbFile.length() > 0) {
                Log.i(TAG, "Database file verification passed");
                return true;
            } else {
                Log.e(TAG, "Database file verification failed");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Database verification failed with exception", e);
            return false;
        }
    }
}
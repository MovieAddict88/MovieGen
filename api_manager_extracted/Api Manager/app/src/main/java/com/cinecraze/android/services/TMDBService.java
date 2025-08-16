package com.cinecraze.android.services;

import android.util.Log;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.SearchResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TMDBService {
    
    private static final String TAG = "TMDBService";
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    
    // Multiple API keys for backup functionality
    private Map<String, String> apiKeys;
    private String currentApiKey = "primary";
    
    private final OkHttpClient client;
    private final Gson gson;
    private final ExecutorService executor;
    
    // New: Use enabled servers only
    private final com.cinecraze.android.utils.DataManager dataManager;
    private final com.cinecraze.android.services.AutoEmbedService autoEmbedService;
    
    public interface MovieCallback {
        void onSuccess(ContentItem movie);
        void onError(String error);
    }
    
    public interface SeriesCallback {
        void onSuccess(List<ContentItem> series);
        void onError(String error);
    }
    
    public interface SearchCallback {
        void onSuccess(List<SearchResult> results);
        void onError(String error);
    }

    // Regional Configuration Class
    public static class RegionalConfig {
        public String originCountry;
        public String language;
        public int genreId;
        public String keywords;

        public RegionalConfig(String originCountry, String language, int genreId, String keywords) {
            this.originCountry = originCountry;
            this.language = language;
            this.genreId = genreId;
            this.keywords = keywords;
        }
    }

    public TMDBService(android.content.Context context) {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(3);
        this.dataManager = com.cinecraze.android.utils.DataManager.getInstance(context);
        this.autoEmbedService = new com.cinecraze.android.services.AutoEmbedService();
        initializeApiKeys();
    }

    // Backward-compat old no-arg constructor (deprecated) - will behave without user-selected servers
    public TMDBService() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(3);
        this.dataManager = null;
        this.autoEmbedService = new com.cinecraze.android.services.AutoEmbedService();
        initializeApiKeys();
    }

    private void initializeApiKeys() {
        apiKeys = new HashMap<>();
        apiKeys.put("primary", "ec926176bf467b3f7735e3154238c161");
        apiKeys.put("backup1", "bb51e18edb221e87a05f90c2eb456069");
        apiKeys.put("backup2", "4a1f2e8c9d3b5a7e6f9c2d1e8b4a5c3f");
        apiKeys.put("backup3", "7d9a2b1e4f6c8e5a3b7d9f2e1c4a6b8d");
    }

    public void setApiKey(String keyName) {
        if (apiKeys.containsKey(keyName)) {
            currentApiKey = keyName;
            Log.d(TAG, "Switched to API key: " + keyName);
        }
    }

    private String getCurrentApiKey() {
        return apiKeys.get(currentApiKey);
    }

    public void getMovieDetails(int tmdbId, MovieCallback callback) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + getCurrentApiKey() + "&append_to_response=credits,videos";
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject movieJson = JsonParser.parseString(json).getAsJsonObject();
                        
                        ContentItem movie = parseMovieFromJson(movieJson);
                        callback.onSuccess(movie);
                        return;
                    } else if (response.code() == 401) {
                        // Try with backup API keys
                        if (tryBackupApiKeys(tmdbId, callback, "movie")) {
                            return;
                        }
                    }
                    callback.onError("Failed to fetch movie: " + response.code() + " - " + response.message());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching movie", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    public void getSeriesDetails(int tmdbId, String seasons, SeriesCallback callback) {
        executor.submit(() -> {
            try {
                String seriesUrl = BASE_URL + "/tv/" + tmdbId + "?api_key=" + getCurrentApiKey();
                Request seriesRequest = new Request.Builder().url(seriesUrl).build();
                
                try (Response seriesResponse = client.newCall(seriesRequest).execute()) {
                    if (seriesResponse.isSuccessful() && seriesResponse.body() != null) {
                        String seriesJson = seriesResponse.body().string();
                        JsonObject seriesData = JsonParser.parseString(seriesJson).getAsJsonObject();
                        
                        List<ContentItem> seriesItems = parseSeriesFromJson(seriesData, seasons);
                        callback.onSuccess(seriesItems);
                        return;
                    } else if (seriesResponse.code() == 401) {
                        // Try with backup API keys
                        if (tryBackupApiKeysForSeries(tmdbId, seasons, callback)) {
                            return;
                        }
                    }
                    callback.onError("Failed to fetch series: " + seriesResponse.code() + " - " + seriesResponse.message());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching series", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    public void searchContent(String query, String searchType, String searchSubtype, SearchCallback callback) {
        executor.submit(() -> {
            try {
                String mediaType = getMediaTypeFromSearchType(searchType);
                
                // Handle "multi" search by searching both movie and TV
                if ("multi".equals(mediaType)) {
                    searchMultiContent(query, callback);
                    return;
                }
                
                // URL encode the query
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String url = BASE_URL + "/search/" + mediaType + "?api_key=" + getCurrentApiKey() + 
                           "&query=" + encodedQuery + "&language=en-US&page=1&include_adult=false";
                
                Log.d(TAG, "Searching URL: " + url);
                
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "Search response: " + json.substring(0, Math.min(200, json.length())));
                        
                        JsonObject searchJson = JsonParser.parseString(json).getAsJsonObject();
                        List<SearchResult> results = parseSearchResults(searchJson, mediaType);
                        callback.onSuccess(results);
                        return;
                    } else if (response.code() == 401) {
                        // Try with backup API keys
                        if (tryBackupApiKeysForSearch(query, searchType, searchSubtype, callback)) {
                            return;
                        }
                    }
                    callback.onError("Search failed: " + response.code() + " - " + response.message());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching content", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    private void searchMultiContent(String query, SearchCallback callback) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            
            // Search for movies
            String movieUrl = BASE_URL + "/search/movie?api_key=" + getCurrentApiKey() + 
                            "&query=" + encodedQuery + "&language=en-US&page=1&include_adult=false";
            
            // Search for TV series
            String tvUrl = BASE_URL + "/search/tv?api_key=" + getCurrentApiKey() + 
                          "&query=" + encodedQuery + "&language=en-US&page=1&include_adult=false";
            
            List<SearchResult> allResults = new ArrayList<>();
            
            // Search movies
            Request movieRequest = new Request.Builder().url(movieUrl).build();
            try (Response movieResponse = client.newCall(movieRequest).execute()) {
                if (movieResponse.isSuccessful() && movieResponse.body() != null) {
                    String movieJson = movieResponse.body().string();
                    JsonObject movieSearchJson = JsonParser.parseString(movieJson).getAsJsonObject();
                    List<SearchResult> movieResults = parseSearchResults(movieSearchJson, "movie");
                    allResults.addAll(movieResults);
                }
            }
            
            // Search TV series
            Request tvRequest = new Request.Builder().url(tvUrl).build();
            try (Response tvResponse = client.newCall(tvRequest).execute()) {
                if (tvResponse.isSuccessful() && tvResponse.body() != null) {
                    String tvJson = tvResponse.body().string();
                    JsonObject tvSearchJson = JsonParser.parseString(tvJson).getAsJsonObject();
                    List<SearchResult> tvResults = parseSearchResults(tvSearchJson, "tv");
                    allResults.addAll(tvResults);
                }
            }
            
            callback.onSuccess(allResults);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in multi search", e);
            callback.onError("Error in multi search: " + e.getMessage());
        }
    }

    public void searchRegionalContent(RegionalConfig config, String subtype, SearchCallback callback) {
        executor.submit(() -> {
            try {
                // For regional content like K-drama, we should search TV series, not movies
                String url = BASE_URL + "/discover/tv?api_key=" + getCurrentApiKey() +
                        "&with_origin_country=" + config.originCountry +
                        "&with_original_language=" + config.language +
                        "&with_genres=" + config.genreId +
                        "&sort_by=popularity.desc&page=1&include_adult=false";
                
                Log.d(TAG, "Regional search URL: " + url);
                
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "Regional search response: " + json.substring(0, Math.min(200, json.length())));
                        
                        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                        List<SearchResult> results = parseSearchResults(jsonObject, "tv");
                        callback.onSuccess(results);
                    } else {
                        callback.onError("Failed to search regional content: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching regional content", e);
                callback.onError("Error searching regional content: " + e.getMessage());
            }
        });
    }

    public List<ContentItem> getContentByYear(int year, String contentType, String genre) {
        List<ContentItem> results = new ArrayList<>();
        try {
            // Convert content type from UI format to API format
            String apiContentType = "movie";
            if ("TV Series".equals(contentType)) {
                apiContentType = "tv";
            }
            
            String url = BASE_URL + "/discover/" + apiContentType +
                    "?api_key=" + getCurrentApiKey() +
                    "&sort_by=popularity.desc&page=1";
            
            // Use correct date parameter based on content type
            if ("tv".equals(apiContentType)) {
                url += "&first_air_date_year=" + year;
            } else {
                url += "&primary_release_year=" + year;
            }
            
            if (genre != null && !genre.isEmpty() && !"All Genres".equals(genre)) {
                url += "&with_genres=" + getGenreId(genre);
            }
            
            Request request = new Request.Builder().url(url).build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray resultsArray = jsonObject.getAsJsonArray("results");
                    
                    for (JsonElement element : resultsArray) {
                        JsonObject item = element.getAsJsonObject();
                        ContentItem contentItem = new ContentItem();
                        
                        // Set TMDB ID first
                        contentItem.setTmdbId(getIntValue(item, "id"));
                        
                        // Use correct field names based on content type
                        if ("tv".equals(apiContentType)) {
                            contentItem.setTitle(getStringValue(item, "name"));
                            contentItem.setYear(getIntValue(item, "first_air_date"));
                        } else {
                            contentItem.setTitle(getStringValue(item, "title"));
                            contentItem.setYear(getIntValue(item, "release_date"));
                        }
                        
                        contentItem.setDescription(getStringValue(item, "overview"));
                        String posterPath = getStringValue(item, "poster_path");
                        if (!posterPath.isEmpty()) {
                            contentItem.setImageUrl(IMAGE_BASE_URL + posterPath);
                        }
                        contentItem.setRating(getDoubleValue(item, "vote_average"));
                        contentItem.setType(contentType);  // Keep original format for UI
                        if (genre != null && !genre.isEmpty()) {
                            contentItem.setSubcategory(genre);
                        }
                        
                        // Generate auto-embed servers based on content type
                        List<String> servers;
                        if ("TV Series".equals(contentType)) {
                            // For TV series, generate servers for season 1 episode 1 by default
                            servers = generateAutoEmbedServersForTV(contentItem.getTmdbId(), 1, 1);
                        } else {
                            // For movies
                            servers = generateAutoEmbedServersForMovie(contentItem.getTmdbId());
                        }
                        contentItem.setServers(servers);
                        
                        results.add(contentItem);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting content by year", e);
        }
        return results;
    }

    public List<ContentItem> getContentByGenre(String genre, String contentType, int maxResults) {
        List<ContentItem> results = new ArrayList<>();
        try {
            // Convert content type from UI format to API format
            String apiContentType = "movie";
            if ("TV Series".equals(contentType)) {
                apiContentType = "tv";
            }
            
            String url = BASE_URL + "/discover/" + apiContentType +
                    "?api_key=" + getCurrentApiKey() +
                    "&with_genres=" + getGenreId(genre) +
                    "&sort_by=popularity.desc&page=1";
            
            Log.d(TAG, "Genre API URL: " + url);
            Request request = new Request.Builder().url(url).build();
            
            try (Response response = client.newCall(request).execute()) {
                Log.d(TAG, "API Response Code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "API Response: " + json.substring(0, Math.min(500, json.length())) + "...");
                    
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray resultsArray = jsonObject.getAsJsonArray("results");
                    
                    if (resultsArray != null) {
                        Log.d(TAG, "Found " + resultsArray.size() + " results from TMDB");
                        
                        int count = 0;
                        for (JsonElement element : resultsArray) {
                            if (count >= maxResults) break;
                            
                            JsonObject item = element.getAsJsonObject();
                            ContentItem contentItem = new ContentItem();
                            
                            // Set TMDB ID first
                            contentItem.setTmdbId(getIntValue(item, "id"));
                            
                            // Use correct field names based on content type
                            if ("tv".equals(apiContentType)) {
                                contentItem.setTitle(getStringValue(item, "name"));
                                contentItem.setYear(getIntValue(item, "first_air_date"));
                            } else {
                                contentItem.setTitle(getStringValue(item, "title"));
                                contentItem.setYear(getIntValue(item, "release_date"));
                            }
                            
                            contentItem.setDescription(getStringValue(item, "overview"));
                            String posterPath = getStringValue(item, "poster_path");
                            if (!posterPath.isEmpty()) {
                                contentItem.setImageUrl(IMAGE_BASE_URL + posterPath);
                            }
                            contentItem.setRating(getDoubleValue(item, "vote_average"));
                            contentItem.setType(contentType);  // Keep original format for UI
                            contentItem.setSubcategory(genre);
                            
                            // Generate auto-embed servers based on content type
                            List<String> servers;
                            if ("TV Series".equals(contentType)) {
                                // For TV series, generate servers for season 1 episode 1 by default
                                servers = generateAutoEmbedServersForTV(contentItem.getTmdbId(), 1, 1);
                            } else {
                                // For movies
                                servers = generateAutoEmbedServersForMovie(contentItem.getTmdbId());
                            }
                            contentItem.setServers(servers);
                            
                            Log.d(TAG, "Created content item: " + contentItem.getTitle() + " (TMDB ID: " + contentItem.getTmdbId() + ") with " + servers.size() + " servers");
                            results.add(contentItem);
                            count++;
                        }
                        
                        Log.d(TAG, "Successfully created " + results.size() + " content items");
                    } else {
                        Log.w(TAG, "No results array found in response");
                    }
                } else {
                    Log.e(TAG, "API request failed: " + response.code() + " - " + response.message());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting content by genre", e);
        }
        return results;
    }

    public void getPopularContent(SearchCallback callback) {
        executor.submit(() -> {
            try {
                // Get popular movies and TV shows
                List<SearchResult> allResults = new ArrayList<>();
                
                // Get popular movies
                String moviesUrl = BASE_URL + "/movie/popular?api_key=" + getCurrentApiKey() + "&language=en-US&page=1";
                Request moviesRequest = new Request.Builder().url(moviesUrl).build();
                
                try (Response moviesResponse = client.newCall(moviesRequest).execute()) {
                    if (moviesResponse.isSuccessful() && moviesResponse.body() != null) {
                        String json = moviesResponse.body().string();
                        JsonObject moviesJson = JsonParser.parseString(json).getAsJsonObject();
                        List<SearchResult> movieResults = parseSearchResults(moviesJson, "movie");
                        allResults.addAll(movieResults);
                    }
                }
                
                // Get popular TV shows
                String tvUrl = BASE_URL + "/tv/popular?api_key=" + getCurrentApiKey() + "&language=en-US&page=1";
                Request tvRequest = new Request.Builder().url(tvUrl).build();
                
                try (Response tvResponse = client.newCall(tvRequest).execute()) {
                    if (tvResponse.isSuccessful() && tvResponse.body() != null) {
                        String json = tvResponse.body().string();
                        JsonObject tvJson = JsonParser.parseString(json).getAsJsonObject();
                        List<SearchResult> tvResults = parseSearchResults(tvJson, "tv");
                        allResults.addAll(tvResults);
                    }
                }
                
                callback.onSuccess(allResults);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting popular content", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    private boolean tryBackupApiKeys(int tmdbId, MovieCallback callback, String type) {
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (entry.getKey().equals(currentApiKey)) continue;
            
            try {
                String url = BASE_URL + "/" + type + "/" + tmdbId + "?api_key=" + entry.getValue() + "&append_to_response=credits,videos";
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject dataJson = JsonParser.parseString(json).getAsJsonObject();
                        
                        ContentItem item = parseMovieFromJson(dataJson);
                        callback.onSuccess(item);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Backup API key " + entry.getKey() + " failed", e);
            }
        }
        return false;
    }

    private boolean tryBackupApiKeysForSeries(int tmdbId, String seasons, SeriesCallback callback) {
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (entry.getKey().equals(currentApiKey)) continue;
            
            try {
                String url = BASE_URL + "/tv/" + tmdbId + "?api_key=" + entry.getValue();
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject dataJson = JsonParser.parseString(json).getAsJsonObject();
                        
                        List<ContentItem> seriesItems = parseSeriesFromJson(dataJson, seasons);
                        callback.onSuccess(seriesItems);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Backup API key " + entry.getKey() + " failed for series", e);
            }
        }
        return false;
    }

    private boolean tryBackupApiKeysForSearch(String query, String searchType, String searchSubtype, SearchCallback callback) {
        String mediaType = getMediaTypeFromSearchType(searchType);
        
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (entry.getKey().equals(currentApiKey)) continue;
            
            try {
                String url = BASE_URL + "/search/" + mediaType + "?api_key=" + entry.getValue() + 
                           "&query=" + query + "&language=en-US&page=1&include_adult=false";
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject searchJson = JsonParser.parseString(json).getAsJsonObject();
                        
                        List<SearchResult> results = parseSearchResults(searchJson, mediaType);
                        callback.onSuccess(results);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Backup API key " + entry.getKey() + " failed for search", e);
            }
        }
        return false;
    }

    private boolean tryBackupApiKeysForRegionalSearch(RegionalConfig config, String subtype, SearchCallback callback) {
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (entry.getKey().equals(currentApiKey)) continue;
            
            try {
                String url = BASE_URL + "/discover/tv?api_key=" + entry.getValue() + 
                           "&language=" + config.language + 
                           "&with_origin_country=" + config.originCountry + 
                           "&with_genres=" + config.genreId + 
                           "&sort_by=popularity.desc&page=1&include_adult=false";
                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject searchJson = JsonParser.parseString(json).getAsJsonObject();
                        
                        List<SearchResult> results = parseSearchResults(searchJson, "tv");
                        callback.onSuccess(results);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Backup API key " + entry.getKey() + " failed for regional search", e);
            }
        }
        return false;
    }

    private ContentItem parseMovieFromJson(JsonObject movieJson) {
        ContentItem movie = new ContentItem();
        movie.setTmdbId(getIntValue(movieJson, "id"));
        movie.setTitle(getStringValue(movieJson, "title"));
        movie.setType("Movie");
        movie.setDescription(getStringValue(movieJson, "overview"));
        movie.setYear(getIntValue(movieJson, "release_date"));
        movie.setRating(getDoubleValue(movieJson, "vote_average"));
        movie.setImageUrl(IMAGE_BASE_URL + getStringValue(movieJson, "poster_path"));
        
        // Add genre
        JsonArray genres = movieJson.getAsJsonArray("genres");
        if (genres != null && genres.size() > 0) {
            String genre = genres.get(0).getAsJsonObject().get("name").getAsString();
            movie.setSubcategory(genre);
        }
        
        // Generate auto-embed servers based on enabled servers only
        List<String> servers = generateAutoEmbedServers(movie.getTitle());
        movie.setServers(servers);
        
        return movie;
    }

    private List<ContentItem> parseSeriesFromJson(JsonObject seriesData, String seasons) {
        List<ContentItem> seriesItems = new ArrayList<>();
        
        String seriesTitle = getStringValue(seriesData, "name");
        String seriesOverview = getStringValue(seriesData, "overview");
        Integer seriesYear = getIntValue(seriesData, "first_air_date");
        Double seriesRating = getDoubleValue(seriesData, "vote_average");
        String seriesPoster = IMAGE_BASE_URL + getStringValue(seriesData, "poster_path");
        int seriesId = getIntValue(seriesData, "id");
        
        // Get genre
        JsonArray genres = seriesData.getAsJsonArray("genres");
        String genre = "Drama";
        if (genres != null && genres.size() > 0) {
            genre = genres.get(0).getAsJsonObject().get("name").getAsString();
        }
        
        // Determine which seasons to include
        List<Integer> seasonNumbers = parseSeasonNumbers(seasons);
        JsonArray seasonsArray = seriesData.getAsJsonArray("seasons");
        if (seasonsArray != null) {
            for (JsonElement seasonElement : seasonsArray) {
                JsonObject seasonData = seasonElement.getAsJsonObject();
                int seasonNumber = getIntValue(seasonData, "season_number");
                
                if (!seasonNumbers.isEmpty() && !seasonNumbers.contains(seasonNumber)) {
                    continue;
                }
                
                try {
                    // Fetch season details to get all episodes
                    String seasonUrl = BASE_URL + "/tv/" + seriesId + "/season/" + seasonNumber + "?api_key=" + getCurrentApiKey();
                    Request request = new Request.Builder().url(seasonUrl).build();
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String seasonJson = response.body().string();
                            JsonObject seasonObj = JsonParser.parseString(seasonJson).getAsJsonObject();
                            JsonArray episodes = seasonObj.getAsJsonArray("episodes");
                            if (episodes != null) {
                                for (JsonElement epEl : episodes) {
                                    JsonObject ep = epEl.getAsJsonObject();
                                    int episodeNumber = getIntValue(ep, "episode_number");
                                    String epName = getStringValue(ep, "name");
                                    String epOverview = getStringValue(ep, "overview");
                                    String airDate = getStringValue(ep, "air_date");
                                    Double epRating = getDoubleValue(ep, "vote_average");
                                    String stillPath = getStringValue(ep, "still_path");
                                    
                                    ContentItem item = new ContentItem();
                                    item.setTmdbId(seriesId);
                                    // Keep title including season and episode for grouping/export
                                    item.setTitle(seriesTitle + " S" + String.format("%02d", seasonNumber) + "E" + String.format("%02d", episodeNumber));
                                    item.setType("TV Series");
                                    item.setSubcategory(genre);
                                    item.setDescription(epOverview != null && !epOverview.isEmpty() ? epOverview : seriesOverview);
                                    item.setYear(airDate != null && airDate.length() >= 4 ? Integer.parseInt(airDate.substring(0,4)) : seriesYear);
                                    item.setRating(epRating != null ? epRating : seriesRating);
                                    item.setImageUrl(stillPath != null && !stillPath.isEmpty() ? (IMAGE_BASE_URL + stillPath) : seriesPoster);
                                    item.setSeason(seasonNumber);
                                    item.setEpisode(episodeNumber);
                                    
                                    // Generate servers for this episode using enabled server configs and series title
                                    String episodeTitle = seriesTitle + " S" + String.format("%02d", seasonNumber) + "E" + String.format("%02d", episodeNumber);
                                    List<String> servers = generateAutoEmbedServers(episodeTitle);
                                    item.setServers(servers);
                                    
                                    seriesItems.add(item);
                                }
                            }
                        } else {
                            Log.w(TAG, "Failed to fetch season details: " + response.code());
                            // Fallback: at least add episode 1 if season fetch fails
                            ContentItem fallback = new ContentItem();
                            fallback.setTmdbId(seriesId);
                            fallback.setTitle(seriesTitle + " S" + String.format("%02d", seasonNumber) + "E01");
                            fallback.setType("TV Series");
                            fallback.setSubcategory(genre);
                            fallback.setDescription(seriesOverview);
                            fallback.setYear(seriesYear);
                            fallback.setRating(seriesRating);
                            fallback.setImageUrl(seriesPoster);
                            fallback.setSeason(seasonNumber);
                            fallback.setEpisode(1);
                            fallback.setServers(generateAutoEmbedServersForTV(seriesId, seasonNumber, 1));
                            seriesItems.add(fallback);
                        }
                    }
                } catch (IOException io) {
                    Log.e(TAG, "Error fetching season details", io);
                }
            }
        }
        
        return seriesItems;
    }

    private List<SearchResult> parseSearchResults(JsonObject searchJson, String mediaType) {
        List<SearchResult> results = new ArrayList<>();
        JsonArray resultsArray = searchJson.getAsJsonArray("results");
        
        if (resultsArray != null) {
            for (JsonElement element : resultsArray) {
                JsonObject result = element.getAsJsonObject();
                
                SearchResult searchResult = new SearchResult();
                searchResult.setId(getIntValue(result, "id"));
                searchResult.setTitle(getStringValue(result, mediaType.equals("movie") ? "title" : "name"));
                searchResult.setMediaType(mediaType);
                searchResult.setOverview(getStringValue(result, "overview"));
                searchResult.setPosterPath(getStringValue(result, "poster_path"));
                searchResult.setVoteAverage(getDoubleValue(result, "vote_average"));
                searchResult.setReleaseDate(getStringValue(result, mediaType.equals("movie") ? "release_date" : "first_air_date"));
                
                results.add(searchResult);
            }
        }
        
        return results;
    }

    private String getMediaTypeFromSearchType(String searchType) {
        if (searchType.contains("Movies")) return "movie";
        if (searchType.contains("TV Series") || searchType.contains("K-Drama") || 
            searchType.contains("C-Drama") || searchType.contains("J-Drama") || 
            searchType.contains("Pinoy") || searchType.contains("Thai") || 
            searchType.contains("Indian") || searchType.contains("Turkish") || 
            searchType.contains("Korean Variety")) {
            return "tv";
        }
        return "multi";
    }

    private List<String> generateAutoEmbedServers(String title) {
        List<String> servers = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) return servers;
        try {
            if (dataManager != null) {
                List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
                servers = autoEmbedService.generateAutoEmbedUrls(title, enabled);
            } else {
                // Fallback to previous behavior if DataManager not available (old constructor)
                String encodedTitle = title.replace(" ", "%20");
                servers.add("VidSrc 1080p|https://vidsrc.to/embed/" + encodedTitle);
                servers.add("VidJoy 1080p|https://vidjoy.to/embed/" + encodedTitle);
                servers.add("MultiEmbed 1080p|https://multiembed.mov/embed/" + encodedTitle);
                servers.add("AutoEmbed 1080p|https://autoembed.cc/embed/" + encodedTitle);
                servers.add("EmbedSU 1080p|https://embed.su/embed/" + encodedTitle);
                servers.add("VidSrcME 1080p|https://vidsrc.me/embed/" + encodedTitle);
                servers.add("FlixHQ 1080p|https://flixhq.to/watch/" + encodedTitle);
                servers.add("HDToday 1080p|https://hdtoday.tv/embed/" + encodedTitle);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error generating servers from enabled configs: " + e.getMessage());
        }
        return servers;
    }

    private List<String> generateAutoEmbedServersForMovie(int tmdbId) {
        // Use title-based embed generation via enabled servers for consistency with UI selection
        // We fetch minimal title when available by a lightweight call is not desired here; instead, rely on caller to set servers after parse
        // Kept for backward compatibility; return empty and rely on generateAutoEmbedServers(title) flow in parseMovieFromJson
        return new ArrayList<>();
    }

    private List<String> generateAutoEmbedServersForTV(int tmdbId, int season, int episode) {
        // Same rationale as movie; rely on title-based generation using enabled servers
        return new ArrayList<>();
    }

    private List<Integer> parseSeasonNumbers(String seasons) {
        List<Integer> seasonNumbers = new ArrayList<>();
        if (seasons == null || seasons.trim().isEmpty()) {
            return seasonNumbers;
        }
        
        String[] parts = seasons.split(",");
        for (String part : parts) {
            try {
                seasonNumbers.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid season number: " + part);
            }
        }
        return seasonNumbers;
    }

    private String getStringValue(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : "";
    }

    private Integer getIntValue(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && !element.isJsonNull()) {
            String value = element.getAsString();
            if (key.equals("release_date") || key.equals("first_air_date")) {
                // Extract year from date string
                if (value.length() >= 4) {
                    try {
                        return Integer.parseInt(value.substring(0, 4));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else {
                try {
                    return element.getAsInt();
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Double getDoubleValue(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsDouble() : null;
    }

    private int getGenreId(String genre) {
        if (genre == null || genre.isEmpty() || "All Genres".equals(genre)) {
            return 28; // Default to Action
        }
        
        // Map genre names to TMDB genre IDs (case-insensitive)
        Map<String, Integer> genreMap = new HashMap<>();
        genreMap.put("action", 28);
        genreMap.put("adventure", 12);
        genreMap.put("animation", 16);
        genreMap.put("comedy", 35);
        genreMap.put("crime", 80);
        genreMap.put("documentary", 99);
        genreMap.put("drama", 18);
        genreMap.put("family", 10751);
        genreMap.put("fantasy", 14);
        genreMap.put("history", 36);
        genreMap.put("horror", 27);
        genreMap.put("music", 10402);
        genreMap.put("mystery", 9648);
        genreMap.put("romance", 10749);
        genreMap.put("science fiction", 878);
        genreMap.put("tv movie", 10770);
        genreMap.put("thriller", 53);
        genreMap.put("war", 10752);
        genreMap.put("western", 37);
        
        // Try exact match first (case-insensitive)
        String normalizedGenre = genre.toLowerCase().trim();
        Integer genreId = genreMap.get(normalizedGenre);
        if (genreId != null) {
            return genreId;
        }
        
        // Try partial matches for compound genre names
        for (Map.Entry<String, Integer> entry : genreMap.entrySet()) {
            if (normalizedGenre.contains(entry.getKey()) || entry.getKey().contains(normalizedGenre)) {
                return entry.getValue();
            }
        }
        
        Log.w(TAG, "Unknown genre: " + genre + ", defaulting to Action");
        return 28; // Default to Action if no match found
    }

    // Synchronous helper to fetch regional content by year and type (tv or movie)
    public List<SearchResult> fetchRegionalByYearBlocking(RegionalConfig config, int year, String contentType) {
        List<SearchResult> results = new ArrayList<>();
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL)
                    .append("/discover/")
                    .append("tv".equals(contentType) ? "tv" : "movie")
                    .append("?api_key=")
                    .append(getCurrentApiKey())
                    .append("&with_origin_country=")
                    .append(config.originCountry)
                    .append("&with_original_language=")
                    .append(config.language)
                    .append("&sort_by=popularity.desc&page=1&include_adult=false");
            if ("movie".equals(contentType)) {
                urlBuilder.append("&primary_release_year=").append(year);
            } else {
                urlBuilder.append("&first_air_date_year=").append(year);
            }
            if (config.genreId > 0) {
                urlBuilder.append("&with_genres=").append(config.genreId);
            }
            Request request = new Request.Builder().url(urlBuilder.toString()).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray arr = jsonObject.getAsJsonArray("results");
                    if (arr != null) {
                        for (JsonElement el : arr) {
                            JsonObject obj = el.getAsJsonObject();
                            SearchResult r = new SearchResult();
                            r.setId(getIntValue(obj, "id"));
                            r.setTitle(getStringValue(obj, "title"));
                            r.setName(getStringValue(obj, "name"));
                            r.setOverview(getStringValue(obj, "overview"));
                            r.setPosterPath(getStringValue(obj, "poster_path"));
                            r.setMediaType("tv".equals(contentType) ? "tv" : "movie");
                            if ("movie".equals(contentType)) {
                                r.setReleaseDate(getStringValue(obj, "release_date"));
                            } else {
                                r.setFirstAirDate(getStringValue(obj, "first_air_date"));
                            }
                            r.setVoteAverage(getDoubleValue(obj, "vote_average"));
                            r.setPopularity(getDoubleValue(obj, "popularity"));
                            results.add(r);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchRegionalByYearBlocking error", e);
        }
        return results;
    }
}
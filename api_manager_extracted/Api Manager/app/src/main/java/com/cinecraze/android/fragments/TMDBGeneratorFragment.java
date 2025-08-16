package com.cinecraze.android.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cinecraze.android.R;
import com.cinecraze.android.adapters.SearchResultsAdapter;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.SearchResult;
import com.cinecraze.android.services.TMDBService;
import com.cinecraze.android.utils.DataManager;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.LinkedHashMap;

public class TMDBGeneratorFragment extends Fragment {

    private AutoCompleteTextView apiKeySelect;
    private TextView currentApiStatus;
    private TextInputEditText movieTmdbId;
    private TextInputEditText seriesTmdbId;
    private TextInputEditText seriesSeasons;
    private TextInputEditText tmdbSearch;
    private AutoCompleteTextView searchType;
    private AutoCompleteTextView searchSubtype;
    private LinearLayout searchInputGroup;
    private LinearLayout regionalBrowseGroup;
    private AutoCompleteTextView yearFilter;
    private AutoCompleteTextView regionalContentType;
    private ProgressBar searchLoading;
    private LinearLayout movieServers;
    private LinearLayout seriesServers;
    private Button generateMovie;
    private Button generateSeries;
    private Button searchTmdb;
    private Button addMovieServer;
    private Button addSeriesServer;
    private RecyclerView searchResults;
    private TextView searchResultsTitle;
    private TextView searchResultsInfo;

    private TMDBService tmdbService;
    private DataManager dataManager;
    private SearchResultsAdapter searchAdapter;
    private List<SearchResult> searchResultsList;

    // Enhanced API Key Management
    private Map<String, String> apiKeys;
    private String currentApiKey = "primary";

    // Regional Content Configuration
    private Map<String, RegionalConfig> regionalConfigs;

    private ExecutorService executor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tmdb_generator, container, false);
        
        initializeViews(view);
        setupListeners();
        initializeServices();
        initializeRegionalConfigs();
        
        // Load some popular content for initial preview
        loadPopularContent();
        
        // Debug: Check if RecyclerView is properly set up
        Log.d("TMDBGenerator", "RecyclerView setup: " + (searchResults != null ? "OK" : "NULL"));
        Log.d("TMDBGenerator", "Adapter setup: " + (searchAdapter != null ? "OK" : "NULL"));
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Setup dropdowns every time the fragment becomes visible to fix tab switching issues
        setupDropdowns();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Clear dropdown adapters to prevent memory leaks and stale references
        clearDropdownAdapters();
        
        // Clear large result sets to free memory when not visible
        if (searchAdapter != null && searchAdapter.getItemCount() > 30) {
            searchAdapter.clearResults();
            if (searchResultsInfo != null) {
                searchResultsInfo.setText("Results cleared to save memory");
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        clearDropdownAdapters();
        
        // Clear large datasets to prevent memory leaks
        if (searchResultsList != null) {
            searchResultsList.clear();
        }
        if (searchAdapter != null) {
            searchAdapter.clearResults();
        }
        
        // Clear Glide cache if fragment had many images
        try {
            com.bumptech.glide.Glide.get(requireContext()).clearMemory();
        } catch (Exception e) {
            Log.w("TMDBGenerator", "Could not clear Glide cache: " + e.getMessage());
        }
    }
    
    // Handle low memory situations
    public void onLowMemory() {
        if (searchAdapter != null && searchAdapter.getItemCount() > 20) {
            searchAdapter.clearResults();
            showStatus("Results cleared due to low memory");
        }
        
        // Clear Glide memory cache
        try {
            com.bumptech.glide.Glide.get(requireContext()).clearMemory();
        } catch (Exception e) {
            Log.w("TMDBGenerator", "Could not clear Glide cache on low memory: " + e.getMessage());
        }
    }
    
    private void clearDropdownAdapters() {
        try {
            if (apiKeySelect != null) apiKeySelect.setAdapter(null);
            if (searchType != null) searchType.setAdapter(null);
            if (searchSubtype != null) searchSubtype.setAdapter(null);
            if (yearFilter != null) yearFilter.setAdapter(null);
            if (regionalContentType != null) regionalContentType.setAdapter(null);
        } catch (Exception e) {
            Log.w("TMDBGenerator", "Error clearing dropdown adapters: " + e.getMessage());
        }
    }

    private void initializeViews(View view) {
        apiKeySelect = view.findViewById(R.id.api_key_select);
        currentApiStatus = view.findViewById(R.id.current_api_status);
        movieTmdbId = view.findViewById(R.id.movie_tmdb_id);
        seriesTmdbId = view.findViewById(R.id.series_tmdb_id);
        seriesSeasons = view.findViewById(R.id.series_seasons);
        tmdbSearch = view.findViewById(R.id.tmdb_search);
        searchType = view.findViewById(R.id.search_type);
        searchSubtype = view.findViewById(R.id.search_subtype);
        searchInputGroup = view.findViewById(R.id.search_input_group);
        regionalBrowseGroup = view.findViewById(R.id.regional_browse_group);
        yearFilter = view.findViewById(R.id.year_filter);
        regionalContentType = view.findViewById(R.id.regional_content_type);
        searchLoading = view.findViewById(R.id.search_loading);
        movieServers = view.findViewById(R.id.movie_servers);
        seriesServers = view.findViewById(R.id.series_servers);
        generateMovie = view.findViewById(R.id.generate_movie);
        generateSeries = view.findViewById(R.id.generate_series);
        searchTmdb = view.findViewById(R.id.search_tmdb);
        addMovieServer = view.findViewById(R.id.add_movie_server);
        addSeriesServer = view.findViewById(R.id.add_series_server);
        searchResults = view.findViewById(R.id.search_results);
        searchResultsTitle = view.findViewById(R.id.search_results_title);
        searchResultsInfo = view.findViewById(R.id.search_results_info);
    }

    private void setupListeners() {
        generateMovie.setOnClickListener(v -> generateMovieFromTMDB());
        generateSeries.setOnClickListener(v -> generateSeriesFromTMDB());
        searchTmdb.setOnClickListener(v -> searchTMDB());
        addMovieServer.setOnClickListener(v -> addMovieServerField());
        addSeriesServer.setOnClickListener(v -> addSeriesServerField());
        
        apiKeySelect.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedKey = (String) parent.getItemAtPosition(position);
            updateApiStatus(selectedKey);
        });
    }

    private void setupDropdowns() {
        // Ensure we have a valid context and views before setting up dropdowns
        if (getContext() == null || apiKeySelect == null) {
            Log.w("TMDBGenerator", "Cannot setup dropdowns - context or views not available");
            return;
        }
        
        try {
            // Enhanced API Key dropdown with multiple keys - create fresh adapter
            String[] apiKeys = {
                "Primary Key (***61)", 
                "Backup Key 1 (***69)", 
                "Backup Key 2 (***3f)", 
                "Backup Key 3 (***8d)"
            };
            ArrayAdapter<String> apiKeyAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, apiKeys);
            apiKeySelect.setAdapter(apiKeyAdapter);

            // Enhanced Search type dropdown with regional content - create fresh adapter
            String[] searchTypes = {
                "🔍 Search Mode", 
                "🎬 Movies", 
                "📺 TV Series", 
                "🌟 K-Drama", 
                "🇨🇳 C-Drama", 
                "🇯🇵 J-Drama", 
                "🇵🇭 Pinoy", 
                "🇹🇭 Thai", 
                "🇮🇳 Indian", 
                "🇹🇷 Turkish", 
                "🎭 Korean Variety"
            };
            ArrayAdapter<String> searchTypeAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, searchTypes);
            searchType.setAdapter(searchTypeAdapter);
            
            // Clear any existing listeners to prevent duplicates
            searchType.setOnItemClickListener(null);
            searchType.setOnItemClickListener((parent, view, position, id) -> {
                String selectedType = searchTypes[position];
                handleSearchTypeChanged(selectedType);
                if (isRegionalContentType(selectedType)) {
                    // Automatically search for popular content in this region
                    performRegionalSearch(selectedType, "All");
                }
            });

            // Enhanced Search subtype dropdown - create fresh adapter
            String[] searchSubtypes = {
                "All", "Action", "Drama", "Comedy", "Thriller", "Romance", 
                "Horror", "Sci-Fi", "Fantasy", "Crime", "Mystery", "Adventure"
            };
            ArrayAdapter<String> searchSubtypeAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, searchSubtypes);
            searchSubtype.setAdapter(searchSubtypeAdapter);
            
            // Clear any existing listeners to prevent duplicates
            searchSubtype.setOnItemClickListener(null);
            searchSubtype.setOnItemClickListener((parent, view, position, id) -> {
                String selectedSubtype = searchSubtypes[position];
                String currentSearchType = searchType.getText().toString();
                if (isRegionalContentType(currentSearchType) && !selectedSubtype.equals("All")) {
                    // Automatically search with the selected subtype
                    performRegionalSearch(currentSearchType, selectedSubtype);
                }
            });

            // Year filter options - create fresh adapter
            String[] yearOptions = {"All Recent (2020-2025)", "All 2010s", "All 2000s", "All Classic (1990s)", "All Time", "2025", "2024", "2023", "2022", "2021", "2020"};
            ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, yearOptions);
            yearFilter.setAdapter(yearAdapter);
            
            // Clear any existing listeners to prevent duplicates
            yearFilter.setOnItemClickListener(null);
            yearFilter.setOnItemClickListener((p, v, pos, i) -> loadRegionalBySelection());

            // Regional content type - create fresh adapter
            String[] regionalTypes = {"Both", "tv", "movie"};
            ArrayAdapter<String> regionalTypeAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, regionalTypes);
            regionalContentType.setAdapter(regionalTypeAdapter);
            
            // Clear any existing listeners to prevent duplicates
            regionalContentType.setOnItemClickListener(null);
            regionalContentType.setOnItemClickListener((p, v, pos, i) -> loadRegionalBySelection());
            
            Log.d("TMDBGenerator", "Dropdowns setup completed successfully");
            
        } catch (Exception e) {
            Log.e("TMDBGenerator", "Error setting up dropdowns: " + e.getMessage(), e);
        }
    }

    private void handleSearchTypeChanged(String selectedType) {
        boolean isRegional = isRegionalContentType(selectedType);
        searchInputGroup.setVisibility(isRegional ? View.GONE : View.VISIBLE);
        regionalBrowseGroup.setVisibility(isRegional ? View.VISIBLE : View.GONE);
        if (isRegional) {
            searchResultsTitle.setText("Popular " + selectedType);
            searchResultsInfo.setText("Select year to browse");
        }
    }

    private void setSearchLoading(boolean loading) {
        if (searchLoading == null) return;
        searchLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void loadRegionalBySelection() {
        String currentSearchType = searchType.getText().toString();
        if (!isRegionalContentType(currentSearchType)) return;
        String yearSel = yearFilter.getText().toString();
        String contentSel = regionalContentType.getText().toString();
        String contentType = contentSel.equalsIgnoreCase("Both") ? "both" : contentSel;

        if (yearSel == null || yearSel.isEmpty()) return;

        setSearchLoading(true);
        searchAdapter.clearResults();

        // Map year labels to API params similar to HTML
        executor.submit(() -> {
            try {
                List<SearchResult> aggregated = new ArrayList<>();
                if (yearSel.startsWith("All Recent")) {
                    for (int y = 2025; y >= 2020; y--) aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                } else if (yearSel.contains("2010s")) {
                    for (int y = 2019; y >= 2010; y--) aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                } else if (yearSel.contains("2000s")) {
                    for (int y = 2009; y >= 2000; y--) aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                } else if (yearSel.contains("Classic")) {
                    for (int y = 1999; y >= 1990; y--) aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                } else if (yearSel.contains("All Time")) {
                    for (int y = 2025; y >= 1990; y--) aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                } else {
                    int y = Integer.parseInt(yearSel);
                    aggregated.addAll(fetchRegionalByYear(currentSearchType, y, contentType));
                }

                // De-duplicate by id and sort like HTML
                Map<Integer, SearchResult> idMap = new LinkedHashMap<>();
                for (SearchResult r : aggregated) idMap.put(r.getId(), r);
                List<SearchResult> unique = new ArrayList<>(idMap.values());
                unique.sort((a, b) -> {
                    String da = a.getDisplayDate();
                    String db = b.getDisplayDate();
                    int ya = (da != null && da.length() >= 4) ? Integer.parseInt(da.substring(0,4)) : 0;
                    int yb = (db != null && db.length() >= 4) ? Integer.parseInt(db.substring(0,4)) : 0;
                    if (yb != ya) return yb - ya;
                    double pa = a.getPopularity() != null ? a.getPopularity() : 0.0;
                    double pb = b.getPopularity() != null ? b.getPopularity() : 0.0;
                    return Double.compare(pb, pa);
                });

                requireActivity().runOnUiThread(() -> {
                    searchResultsList.clear();
                    searchResultsList.addAll(unique);
                    
                    // Use the adapter's optimized method instead of notifyDataSetChanged
                    searchAdapter.setSearchResults(unique);
                    
                    setSearchLoading(false);
                    searchResultsTitle.setVisibility(View.VISIBLE);
                    searchResultsInfo.setVisibility(View.VISIBLE);
                    searchResultsTitle.setText(currentSearchType + " " + yearSel);
                    
                    // Show pagination info if there are many results
                    String resultText;
                    if (unique.size() > 20) {
                        resultText = unique.size() + " results found";
                        if (searchAdapter != null && searchAdapter.hasMoreItems()) {
                            resultText += " (showing first " + searchAdapter.getItemCount() + " - scroll down for more)";
                        }
                    } else {
                        resultText = unique.size() + " results";
                    }
                    searchResultsInfo.setText(resultText);
                });
            } catch (Exception ex) {
                requireActivity().runOnUiThread(() -> {
                    setSearchLoading(false);
                    searchResultsInfo.setText("Error: " + ex.getMessage());
                });
            }
        });
    }

    private List<SearchResult> fetchRegionalByYear(String regionLabel, int year, String contentType) {
        // Map region label back to config key used in TMDBService
        RegionalConfig cfg = regionalConfigs.get(regionLabel);
        if (cfg == null) return new ArrayList<>();
        TMDBService.RegionalConfig tmdbCfg = new TMDBService.RegionalConfig(cfg.originCountry, cfg.language, cfg.genreId, cfg.keywords);

        List<SearchResult> all = new ArrayList<>();
        // movies or tv or both
        if ("both".equals(contentType)) {
            all.addAll(tmdbService.fetchRegionalByYearBlocking(tmdbCfg, year, "tv"));
            all.addAll(tmdbService.fetchRegionalByYearBlocking(tmdbCfg, year, "movie"));
        } else {
            all.addAll(tmdbService.fetchRegionalByYearBlocking(tmdbCfg, year, contentType));
        }
        return all;
    }

    private void initializeServices() {
        tmdbService = new TMDBService(requireContext());
        dataManager = DataManager.getInstance(requireContext());
        searchResultsList = new ArrayList<>();
        
        // Initialize adapter with empty constructor to prevent loading issues
        searchAdapter = new SearchResultsAdapter();
        
        // Set up listeners after adapter is created
        searchAdapter.setOnItemClickListener(result -> {
            // Handle search result selection - auto-fill the appropriate field
            if (result.getMediaType().equals("movie")) {
                movieTmdbId.setText(String.valueOf(result.getId()));
                Toast.makeText(requireContext(), 
                    "Selected: " + result.getDisplayTitle() + " (Movie)", Toast.LENGTH_SHORT).show();
            } else {
                seriesTmdbId.setText(String.valueOf(result.getId()));
                Toast.makeText(requireContext(), 
                    "Selected: " + result.getDisplayTitle() + " (TV Series)", Toast.LENGTH_SHORT).show();
            }
        });

        // NEW: direct-generate from preview item
        searchAdapter.setOnGenerateClickListener(result -> {
            if (result == null) return;
            if ("movie".equals(result.getMediaType())) {
                addMovieById(result.getId(), result.getDisplayTitle());
            } else {
                addSeriesById(result.getId(), result.getDisplayTitle());
            }
        });
        
        // Set up load more listener for pagination
        searchAdapter.setOnLoadMoreListener(() -> {
            Log.d("TMDBGenerator", "Load more requested");
            // The adapter handles loading more items automatically
            // We just need to update the UI if needed
            if (searchResultsInfo != null) {
                String resultText = "Loading more results...";
                searchResultsInfo.setText(resultText);
            }
        });
        
        // Use GridLayoutManager for better presentation like the HTML version
        int spanCount = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        
        // Make the load more button span all columns
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // If this is the load more button (last item when there are more items), span all columns
                if (searchAdapter != null && searchAdapter.getItemViewType(position) == 1) { // VIEW_TYPE_LOADING_MORE = 1
                    return spanCount; // Span all columns
                }
                return 1; // Regular items take 1 column
            }
        });
        
        searchResults.setLayoutManager(gridLayoutManager);
        searchResults.setAdapter(searchAdapter);
        
        // Optimize RecyclerView for large datasets
        searchResults.setHasFixedSize(true);
        searchResults.setItemViewCacheSize(20);
        searchResults.getRecycledViewPool().setMaxRecycledViews(0, 30);

        executor = Executors.newFixedThreadPool(5); // Create a thread pool for concurrent searches
    }

    private void initializeRegionalConfigs() {
        // Initialize API keys
        apiKeys = new HashMap<>();
        apiKeys.put("primary", "ec926176bf467b3f7735e3154238c161");
        apiKeys.put("backup1", "bb51e18edb221e87a05f90c2eb456069");
        apiKeys.put("backup2", "4a1f2e8c9d3b5a7e6f9c2d1e8b4a5c3f");
        apiKeys.put("backup3", "7d9a2b1e4f6c8e5a3b7d9f2e1c4a6b8d");

        // Initialize regional configurations
        regionalConfigs = new HashMap<>();
        regionalConfigs.put("🌟 K-Drama", new RegionalConfig("KR", "ko", 18, "korean drama"));
        regionalConfigs.put("🇨🇳 C-Drama", new RegionalConfig("CN", "zh", 18, "chinese drama"));
        regionalConfigs.put("🇯🇵 J-Drama", new RegionalConfig("JP", "ja", 18, "japanese drama"));
        regionalConfigs.put("🇵🇭 Pinoy", new RegionalConfig("PH", "tl", 18, "filipino series"));
        regionalConfigs.put("🇹🇭 Thai", new RegionalConfig("TH", "th", 18, "thai drama"));
        regionalConfigs.put("🇮🇳 Indian", new RegionalConfig("IN", "hi", 18, "indian series"));
        regionalConfigs.put("🇹🇷 Turkish", new RegionalConfig("TR", "tr", 18, "turkish drama"));
        regionalConfigs.put("🎭 Korean Variety", new RegionalConfig("KR", "ko", 10764, "korean variety"));
    }

    private void updateApiStatus(String selectedKey) {
        // Extract key name from display text
        String keyName = selectedKey.split(" ")[0].toLowerCase();
        if (keyName.equals("primary")) {
            currentApiKey = "primary";
        } else if (keyName.equals("backup")) {
            // Extract backup number
            String backupNum = selectedKey.split(" ")[2].replace("(", "").replace(")", "");
            currentApiKey = "backup" + backupNum;
        }
        
        currentApiStatus.setText(selectedKey + " (Active)");
        tmdbService.setApiKey(getCurrentApiKey());
        Toast.makeText(requireContext(), "Switched to API key: " + selectedKey, Toast.LENGTH_SHORT).show();
    }

    private String getCurrentApiKey() {
        return apiKeys.get(currentApiKey);
    }

    private boolean isRegionalContentType(String searchType) {
        return regionalConfigs.containsKey(searchType);
    }

    private void performRegionalSearch(String searchType, String searchSubtype) {
        Log.d("TMDBGenerator", "Regional search - Type: '" + searchType + "', Subtype: '" + searchSubtype + "'");
        
        if (!isRegionalContentType(searchType)) {
            Log.w("TMDBGenerator", "Not a regional content type: " + searchType);
            showStatus("Not a regional content type");
            return;
        }
        
        RegionalConfig config = regionalConfigs.get(searchType);
        if (config == null) {
            Log.e("TMDBGenerator", "Regional config not found for: " + searchType);
            showStatus("Regional config not found for: " + searchType);
            return;
        }
        
        Log.d("TMDBGenerator", "Regional config found - Country: " + config.originCountry + 
              ", Language: " + config.language + ", Genre: " + config.genreId);
        
        // Convert to TMDBService.RegionalConfig
        TMDBService.RegionalConfig tmdbConfig = new TMDBService.RegionalConfig(
            config.originCountry, config.language, config.genreId, config.keywords
        );
        
        showStatus("Searching " + searchType + " content...");
        searchResultsTitle.setText("Popular " + searchType);
        searchResultsInfo.setText("Loading popular content...");
        
        tmdbService.searchRegionalContent(tmdbConfig, searchSubtype, new TMDBService.SearchCallback() {
            @Override
            public void onSuccess(List<SearchResult> results) {
                Log.d("TMDBGenerator", "Regional search successful, found " + results.size() + " results");
                requireActivity().runOnUiThread(() -> {
                    searchResultsList.clear();
                    searchResultsList.addAll(results);
                    
                    // Use the adapter's optimized method instead of notifyDataSetChanged
                    searchAdapter.setSearchResults(results);
                    
                    String resultText = results.size() + " results found";
                    if (searchAdapter != null && searchAdapter.hasMoreItems()) {
                        resultText += " (showing " + searchAdapter.getItemCount() + " of " + searchAdapter.getTotalItemCount() + ")";
                    }
                    searchResultsInfo.setText(resultText);
                    
                    if (results.isEmpty()) {
                        Toast.makeText(requireContext(), "No " + searchType + " content found", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Found " + results.size() + " " + searchType + " items", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e("TMDBGenerator", "Regional search failed: " + error);
                requireActivity().runOnUiThread(() -> {
                    searchResultsInfo.setText("Error: " + error);
                    Toast.makeText(requireContext(), "Search failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadPopularContent() {
        searchResultsTitle.setText("Popular Movies & TV Shows");
        searchResultsInfo.setText("Loading popular content...");
        
        tmdbService.getPopularContent(new TMDBService.SearchCallback() {
            @Override
            public void onSuccess(List<SearchResult> results) {
                requireActivity().runOnUiThread(() -> {
                    searchResultsList.clear();
                    searchResultsList.addAll(results);
                    
                    // Use the adapter's optimized method instead of notifyDataSetChanged
                    searchAdapter.setSearchResults(results);
                    
                    String resultText = results.size() + " popular items loaded";
                    if (searchAdapter != null && searchAdapter.hasMoreItems()) {
                        resultText += " (showing " + searchAdapter.getItemCount() + " of " + searchAdapter.getTotalItemCount() + ")";
                    }
                    searchResultsInfo.setText(resultText);
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    searchResultsInfo.setText("Error loading popular content: " + error);
                });
            }
        });
    }

    private void generateMovieFromTMDB() {
        String tmdbId = movieTmdbId.getText().toString().trim();
        if (tmdbId.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a TMDB Movie ID", Toast.LENGTH_SHORT).show();
            return;
        }

        generateMovie.setEnabled(false);
        generateMovie.setText("Generating...");

        tmdbService.getMovieDetails(Integer.parseInt(tmdbId), new TMDBService.MovieCallback() {
            @Override
            public void onSuccess(ContentItem movie) {
                // Add additional servers (append to auto-generated servers)
                List<String> additionalServers = getAdditionalServers(movieServers);
                List<String> combinedServers = new ArrayList<>();
                if (movie.getServers() != null) {
                    combinedServers.addAll(movie.getServers());
                }
                if (additionalServers != null && !additionalServers.isEmpty()) {
                    combinedServers.addAll(additionalServers);
                }
                movie.setServers(combinedServers);
                
                // Save to data manager
                dataManager.addContent(movie);
                
                requireActivity().runOnUiThread(() -> {
                    generateMovie.setEnabled(true);
                    generateMovie.setText("Generate Movie");
                    Toast.makeText(requireContext(), "Movie generated successfully!", Toast.LENGTH_SHORT).show();
                    clearMovieForm();
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    generateMovie.setEnabled(true);
                    generateMovie.setText("Generate Movie");
                    Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void generateSeriesFromTMDB() {
        String tmdbId = seriesTmdbId.getText().toString().trim();
        String seasons = seriesSeasons.getText().toString().trim();
        
        if (tmdbId.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a TMDB TV Series ID", Toast.LENGTH_SHORT).show();
            return;
        }

        generateSeries.setEnabled(false);
        generateSeries.setText("Generating...");

        tmdbService.getSeriesDetails(Integer.parseInt(tmdbId), seasons, new TMDBService.SeriesCallback() {
            @Override
            public void onSuccess(List<ContentItem> series) {
                // Add additional servers (append to auto-generated servers)
                List<String> additionalServers = getAdditionalServers(seriesServers);
                for (ContentItem item : series) {
                    List<String> combinedServers = new ArrayList<>();
                    if (item.getServers() != null) {
                        combinedServers.addAll(item.getServers());
                    }
                    if (additionalServers != null && !additionalServers.isEmpty()) {
                        combinedServers.addAll(additionalServers);
                    }
                    item.setServers(combinedServers);
                }
                
                // Save to data manager
                dataManager.addContentList(series);
                
                requireActivity().runOnUiThread(() -> {
                    generateSeries.setEnabled(true);
                    generateSeries.setText("Generate Series");
                    Toast.makeText(requireContext(), 
                        "Series generated successfully! (" + series.size() + " items)", 
                        Toast.LENGTH_SHORT).show();
                    clearSeriesForm();
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    generateSeries.setEnabled(true);
                    generateSeries.setText("Generate Series");
                    Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void searchTMDB() {
        String query = tmdbSearch.getText().toString().trim();
        String searchTypeText = searchType.getText().toString();
        String searchSubtypeText = searchSubtype.getText().toString();
        
        Log.d("TMDBGenerator", "Search request - Query: '" + query + "', Type: '" + searchTypeText + "', Subtype: '" + searchSubtypeText + "'");
        
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a search query", Toast.LENGTH_SHORT).show();
            return;
        }

        searchTmdb.setEnabled(false);
        searchTmdb.setText("Searching...");
        searchResultsTitle.setText("Search Results");
        searchResultsInfo.setText("Searching...");

        Log.d("TMDBGenerator", "Starting search with TMDB service...");
        
        tmdbService.searchContent(query, searchTypeText, searchSubtypeText, new TMDBService.SearchCallback() {
            @Override
            public void onSuccess(List<SearchResult> results) {
                Log.d("TMDBGenerator", "Search successful, found " + results.size() + " results");
                requireActivity().runOnUiThread(() -> {
                    searchResultsList.clear();
                    searchResultsList.addAll(results);
                    
                    // Use the adapter's optimized method instead of notifyDataSetChanged
                    searchAdapter.setSearchResults(results);
                    
                    searchResultsTitle.setVisibility(View.VISIBLE);
                    searchResultsInfo.setVisibility(View.VISIBLE);
                    
                    String resultText;
                    if (results.size() > 20) {
                        resultText = results.size() + " results found";
                        if (searchAdapter != null && searchAdapter.hasMoreItems()) {
                            resultText += " (showing first " + searchAdapter.getItemCount() + " - scroll down for more)";
                        }
                    } else {
                        resultText = results.size() + " results found";
                    }
                    searchResultsInfo.setText(resultText);
                    
                    searchTmdb.setEnabled(true);
                    searchTmdb.setText("Search TMDB");
                    
                    if (results.isEmpty()) {
                        Toast.makeText(requireContext(), "No results found for: " + query, Toast.LENGTH_SHORT).show();
                    } else {
                        String toastMessage = "Found " + results.size() + " results";
                        if (results.size() > 20) {
                            toastMessage += " - scroll down to load more";
                        }
                        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e("TMDBGenerator", "Search failed: " + error);
                requireActivity().runOnUiThread(() -> {
                    searchResultsInfo.setText("Error: " + error);
                    searchTmdb.setEnabled(true);
                    searchTmdb.setText("Search TMDB");
                    Toast.makeText(requireContext(), "Search failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addMovieServerField() {
        addServerField();
    }

    private void addSeriesServerField() {
        addServerField();
    }

    private void addServerField() {
        View serverView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_server_input, movieServers, false);
        
        TextInputEditText serverName = serverView.findViewById(R.id.server_name);
        TextInputEditText serverUrl = serverView.findViewById(R.id.server_url);
        Button removeButton = serverView.findViewById(R.id.remove_server);
        
        // Set default values
        serverName.setText("Server " + (movieServers.getChildCount() + 1));
        serverUrl.setText("https://example.com/embed/");
        
        removeButton.setOnClickListener(v -> {
            movieServers.removeView(serverView);
        });
        
        movieServers.addView(serverView);
    }

    private List<String> getAdditionalServers(LinearLayout container) {
        List<String> servers = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                TextInputEditText serverName = child.findViewById(R.id.server_name);
                TextInputEditText serverUrl = child.findViewById(R.id.server_url);
                
                if (serverName != null && serverUrl != null) {
                    String name = serverName.getText().toString().trim();
                    String url = serverUrl.getText().toString().trim();
                    
                    if (!name.isEmpty() && !url.isEmpty()) {
                        servers.add(name + "|" + url);
                    }
                }
            }
        }
        return servers;
    }

    private void clearMovieForm() {
        movieTmdbId.setText("");
        movieServers.removeAllViews();
        addMovieServerField(); // Add one empty server field
    }

    private void clearSeriesForm() {
        seriesTmdbId.setText("");
        seriesSeasons.setText("");
        seriesServers.removeAllViews();
        addSeriesServerField(); // Add one empty server field
    }

    private void showStatus(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Add movie directly to DataManager
    private void addMovieById(int tmdbId, String displayTitle) {
        Toast.makeText(requireContext(), "Generating \uD83C\uDFAC " + displayTitle, Toast.LENGTH_SHORT).show();
        tmdbService.getMovieDetails(tmdbId, new TMDBService.MovieCallback() {
            @Override
            public void onSuccess(ContentItem movie) {
                // Preserve auto-generated servers and append any additional from UI
                List<String> additionalServers = getAdditionalServers(movieServers);
                List<String> combined = new ArrayList<>();
                if (movie.getServers() != null) combined.addAll(movie.getServers());
                if (additionalServers != null && !additionalServers.isEmpty()) combined.addAll(additionalServers);
                movie.setServers(combined);
                dataManager.addContent(movie);
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Added to playlist: " + movie.getTitle(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Generate failed: " + error, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // Add series directly to DataManager (adds one season entry per TMDB seasons returned)
    private void addSeriesById(int tmdbId, String displayTitle) {
        Toast.makeText(requireContext(), "Generating \uD83D\uDCFA " + displayTitle, Toast.LENGTH_SHORT).show();
        String seasons = seriesSeasons.getText() != null ? seriesSeasons.getText().toString().trim() : "";
        tmdbService.getSeriesDetails(tmdbId, seasons, new TMDBService.SeriesCallback() {
            @Override
            public void onSuccess(List<ContentItem> series) {
                List<String> additionalServers = getAdditionalServers(seriesServers);
                for (ContentItem item : series) {
                    List<String> combined = new ArrayList<>();
                    if (item.getServers() != null) combined.addAll(item.getServers());
                    if (additionalServers != null && !additionalServers.isEmpty()) combined.addAll(additionalServers);
                    item.setServers(combined);
                }
                dataManager.addContentList(series);
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Added to playlist: " + series.size() + " item(s)", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Generate failed: " + error, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // Regional Configuration Class
    private static class RegionalConfig {
        String originCountry;
        String language;
        int genreId;
        String keywords;

        RegionalConfig(String originCountry, String language, int genreId, String keywords) {
            this.originCountry = originCountry;
            this.language = language;
            this.genreId = genreId;
            this.keywords = keywords;
        }
    }
}
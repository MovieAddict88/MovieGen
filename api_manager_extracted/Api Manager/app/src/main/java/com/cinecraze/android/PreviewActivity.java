package com.cinecraze.android;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.utils.DataManager;
import java.util.ArrayList;
import java.util.List;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.material.appbar.MaterialToolbar;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import com.cinecraze.android.services.TMDBService;
import com.cinecraze.android.services.AutoEmbedService;

public class PreviewActivity extends AppCompatActivity implements PreviewAdapter.ItemActionsListener {

    private RecyclerView recyclerView;
    private PreviewAdapter adapter;
    private DataManager dataManager;
    private TMDBService tmdbService;
    private AutoEmbedService autoEmbedService;
    private AutoCompleteTextView filterDropdown;
    private String currentFilter = "all";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        MaterialToolbar toolbar = findViewById(R.id.preview_toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.preview_list);
        int span = 3;
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);

        adapter = new PreviewAdapter();
        adapter.setActionsListener(this);
        recyclerView.setAdapter(adapter);

        dataManager = DataManager.getInstance(this);
        tmdbService = new TMDBService(this);
        autoEmbedService = new AutoEmbedService();

        // Setup filter dropdown
        setupFilterDropdown();

        // Setup auto-embed action buttons
        setupAutoEmbedButtons();

        adapter.setItems(getPreviewItems());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preview_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_fix_missing_meta) {
            Toast.makeText(this, "Use per-item Generate or series Update Meta", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_fix_missing_servers) {
            Toast.makeText(this, "Use Servers / Servers All", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_fill_missing_episodes) {
            Toast.makeText(this, "Use Fill Missing S/E on the series card", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Per-item actions
    @Override
    public void onGenerateMetadata(ContentItem item, Integer tmdbIdOverride) {
        if ("Live TV".equals(item.getType())) {
            Toast.makeText(this, "TMDB not supported for Live TV", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer id = tmdbIdOverride != null ? tmdbIdOverride : item.getTmdbId();
        if (id == null) {
            Toast.makeText(this, "TMDB ID required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading state
        Toast.makeText(this, "Generating metadata and auto-embed servers...", Toast.LENGTH_SHORT).show();
        
        if ("Movie".equals(item.getType())) {
            tmdbService.getMovieDetails(id, new TMDBService.MovieCallback() {
                @Override public void onSuccess(ContentItem movie) {
                    // Update metadata
                    mergeMetadata(item, movie);
                    
                    // Add auto-embed servers
                    List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
                    List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
                    
                    // Preserve existing servers and add new auto-embed servers
                    List<String> existingServers = item.getServers() != null ? new ArrayList<>(item.getServers()) : new ArrayList<>();
                    existingServers.addAll(servers);
                    item.setServers(existingServers);
                    
                    dataManager.updateContent(item);
                    refreshList();
                    Toast.makeText(PreviewActivity.this, "Movie updated with metadata and auto-embed servers", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String error) {
                    Toast.makeText(PreviewActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            tmdbService.getSeriesDetails(id, "", new TMDBService.SeriesCallback() {
                @Override public void onSuccess(List<ContentItem> series) {
                    if (!series.isEmpty()) {
                        ContentItem base = series.get(0);
                        mergeMetadata(item, base);
                        
                        // Add auto-embed servers
                        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
                        List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
                        
                        // Preserve existing servers and add new auto-embed servers
                        List<String> existingServers = item.getServers() != null ? new ArrayList<>(item.getServers()) : new ArrayList<>();
                        existingServers.addAll(servers);
                        item.setServers(existingServers);
                        
                        dataManager.updateContent(item);
                        refreshList();
                        Toast.makeText(PreviewActivity.this, "Series updated with metadata and auto-embed servers", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onError(String error) {
                    Toast.makeText(PreviewActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onGenerateServers(ContentItem item) {
        if ("Live TV".equals(item.getType())) {
            Toast.makeText(this, "Auto-embed servers are for Movies/Series", Toast.LENGTH_SHORT).show();
            return;
        }
        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
        List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
        item.setServers(servers);
        dataManager.updateContent(item);
        refreshList();
        Toast.makeText(this, "Servers added", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFillSeasonsEpisodes(ContentItem item) {
        if (!"TV Series".equals(item.getType()) || item.getTmdbId() == null) {
            Toast.makeText(this, "TMDB ID required for series", Toast.LENGTH_SHORT).show();
            return;
        }
        tmdbService.getSeriesDetails(item.getTmdbId(), "", new TMDBService.SeriesCallback() {
            @Override public void onSuccess(List<ContentItem> series) {
                int added = 0;
                for (ContentItem ep : series) {
                    if (!dataManager.isDuplicate(ep)) {
                        dataManager.addContent(ep);
                        added++;
                    }
                }
                refreshList();
                Toast.makeText(PreviewActivity.this, "Added " + added + " episode(s)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String error) {
                Toast.makeText(PreviewActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Group-level actions
    @Override
    public void onFillMissingForSeries(String seriesTitle, Integer tmdbId) {
        if (tmdbId == null) {
            Toast.makeText(this, "TMDB ID required for series", Toast.LENGTH_SHORT).show();
            return;
        }
        tmdbService.getSeriesDetails(tmdbId, "", new TMDBService.SeriesCallback() {
            @Override public void onSuccess(List<ContentItem> series) {
                int added = 0;
                for (ContentItem ep : series) {
                    if (!dataManager.isDuplicate(ep)) {
                        dataManager.addContent(ep);
                        added++;
                    }
                }
                refreshList();
                Toast.makeText(PreviewActivity.this, "Filled missing: " + added + " episode(s)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String error) { Toast.makeText(PreviewActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override
    public void onGenerateServersForSeries(String seriesTitle) {
        List<ContentItem> all = dataManager.getAllContent();
        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
        int updated = 0;
        for (ContentItem it : all) {
            if ("TV Series".equals(it.getType())) {
                String st = it.getSeriesTitle() != null && !it.getSeriesTitle().trim().isEmpty() ? it.getSeriesTitle() : extractSeriesTitle(it.getTitle());
                if (seriesTitle.equals(st) && (it.getServers()==null || it.getServers().isEmpty())) {
                    List<String> servers = autoEmbedService.generateAutoEmbedUrls(it.getDisplayTitle(), enabled);
                    it.setServers(servers);
                    dataManager.updateContent(it);
                    updated++;
                }
            }
        }
        refreshList();
        Toast.makeText(this, "Servers updated for "+updated+" item(s)", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpdateMetadataForSeries(String seriesTitle, Integer tmdbId) {
        if (tmdbId == null) {
            Toast.makeText(this, "TMDB ID required for series", Toast.LENGTH_SHORT).show();
            return;
        }
        tmdbService.getSeriesDetails(tmdbId, "", new TMDBService.SeriesCallback() {
            @Override public void onSuccess(List<ContentItem> series) {
                if (series.isEmpty()) { Toast.makeText(PreviewActivity.this, "No data", Toast.LENGTH_SHORT).show(); return; }
                ContentItem base = series.get(0);
                int updated = 0;
                List<ContentItem> all = dataManager.getAllContent();
                for (ContentItem it : all) {
                    if ("TV Series".equals(it.getType())) {
                        String st = it.getSeriesTitle() != null && !it.getSeriesTitle().trim().isEmpty() ? it.getSeriesTitle() : extractSeriesTitle(it.getTitle());
                        if (seriesTitle.equals(st)) {
                            mergeMetadata(it, base);
                            dataManager.updateContent(it);
                            updated++;
                        }
                    }
                }
                refreshList();
                Toast.makeText(PreviewActivity.this, "Updated meta for "+updated+" item(s)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String error) { Toast.makeText(PreviewActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void mergeMetadata(ContentItem target, ContentItem source) {
        if (target.getDescription() == null || target.getDescription().trim().isEmpty()) target.setDescription(source.getDescription());
        if (target.getImageUrl() == null || target.getImageUrl().trim().isEmpty()) target.setImageUrl(source.getImageUrl());
        if (target.getRating() == null) target.setRating(source.getRating());
        if (target.getYear() == null) target.setYear(source.getYear());
        if (target.getSubcategory() == null || target.getSubcategory().trim().isEmpty()) target.setSubcategory(source.getSubcategory());
    }

    private void setupFilterDropdown() {
        filterDropdown = findViewById(R.id.filter_dropdown);
        
        String[] filterOptions = {"All Content", "Movies", "TV Series", "Live TV"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, filterOptions);
        filterDropdown.setAdapter(adapter);
        
        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selected = filterOptions[position];
            switch (selected) {
                case "All Content":
                    currentFilter = "all";
                    break;
                case "Movies":
                    currentFilter = "movie";
                    break;
                case "TV Series":
                    currentFilter = "series";
                    break;
                case "Live TV":
                    currentFilter = "live";
                    break;
            }
            refreshList();
        });
    }

    private void setupAutoEmbedButtons() {
        findViewById(R.id.btn_auto_embed_movies).setOnClickListener(v -> applyAutoEmbedToMovies());
        findViewById(R.id.btn_auto_embed_series).setOnClickListener(v -> applyAutoEmbedToSeries());
        findViewById(R.id.btn_auto_embed_all).setOnClickListener(v -> applyAutoEmbedToAll());
        findViewById(R.id.btn_auto_generate_missing).setOnClickListener(v -> detectAndGenerateMissingContent());
    }

    private List<ContentItem> getPreviewItems() {
        List<ContentItem> all = dataManager.getAllContent();
        List<ContentItem> filtered = new ArrayList<>();
        if (all != null) {
            for (ContentItem it : all) {
                String type = it.getType();
                
                // Apply filter
                boolean shouldInclude = false;
                switch (currentFilter) {
                    case "all":
                        shouldInclude = "Movie".equals(type) || "TV Series".equals(type) || "Live TV".equals(type);
                        break;
                    case "movie":
                        shouldInclude = "Movie".equals(type);
                        break;
                    case "series":
                        shouldInclude = "TV Series".equals(type);
                        break;
                    case "live":
                        shouldInclude = "Live TV".equals(type);
                        break;
                }
                
                if (shouldInclude) {
                    filtered.add(it);
                }
            }
        }
        return filtered;
    }

    private void refreshList() {
        adapter.setItems(getPreviewItems());
    }

    private static String extractSeriesTitle(String episodeTitle){ if(episodeTitle==null||episodeTitle.trim().isEmpty()) return "Unknown Series"; String t=episodeTitle.trim(); t=t.replaceAll("\\s+S\\d+E\\d+.*$",""); t=t.replaceAll("\\s+Season\\s+\\d+.*$",""); if(t.matches(".*\\s+-\\s+.*")){ String[] p=t.split("\\s+-\\s+",2); if(p.length>1){ String f=p[0].trim(); if(f.length()>3) t=f; } } t=t.replaceAll("\\s+(Episode|Ep)\\s+\\d+.*$",""); t=t.replaceAll("\\s+\\d+x\\d+.*$",""); return t.trim(); }

    // Auto-embed methods
    private void applyAutoEmbedToMovies() {
        List<ContentItem> all = dataManager.getAllContent();
        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
        int updated = 0;
        
        for (ContentItem item : all) {
            if ("Movie".equals(item.getType())) {
                List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
                List<String> existingServers = item.getServers() != null ? new ArrayList<>(item.getServers()) : new ArrayList<>();
                existingServers.addAll(servers);
                item.setServers(existingServers);
                dataManager.updateContent(item);
                updated++;
            }
        }
        
        refreshList();
        Toast.makeText(this, "Auto-embed servers applied to " + updated + " movies", Toast.LENGTH_SHORT).show();
    }

    private void applyAutoEmbedToSeries() {
        List<ContentItem> all = dataManager.getAllContent();
        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
        int updated = 0;
        
        for (ContentItem item : all) {
            if ("TV Series".equals(item.getType())) {
                List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
                List<String> existingServers = item.getServers() != null ? new ArrayList<>(item.getServers()) : new ArrayList<>();
                existingServers.addAll(servers);
                item.setServers(existingServers);
                dataManager.updateContent(item);
                updated++;
            }
        }
        
        refreshList();
        Toast.makeText(this, "Auto-embed servers applied to " + updated + " TV series", Toast.LENGTH_SHORT).show();
    }

    private void applyAutoEmbedToAll() {
        List<ContentItem> all = dataManager.getAllContent();
        List<com.cinecraze.android.models.ServerConfig> enabled = dataManager.getEnabledServerConfigs();
        int updated = 0;
        
        for (ContentItem item : all) {
            if ("Movie".equals(item.getType()) || "TV Series".equals(item.getType())) {
                List<String> servers = autoEmbedService.generateAutoEmbedUrls(item.getDisplayTitle(), enabled);
                List<String> existingServers = item.getServers() != null ? new ArrayList<>(item.getServers()) : new ArrayList<>();
                existingServers.addAll(servers);
                item.setServers(existingServers);
                dataManager.updateContent(item);
                updated++;
            }
        }
        
        refreshList();
        Toast.makeText(this, "Auto-embed servers applied to " + updated + " items", Toast.LENGTH_SHORT).show();
    }

    private void detectAndGenerateMissingContent() {
        Toast.makeText(this, "Auto-generating missing seasons and episodes...", Toast.LENGTH_SHORT).show();
        
        // This would implement the logic from cinecraze.html to detect and generate missing content
        // For now, we'll show a placeholder message
        Toast.makeText(this, "Missing content detection and generation feature coming soon", Toast.LENGTH_LONG).show();
    }
}
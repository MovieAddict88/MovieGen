package com.cinecraze.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cinecraze.android.R;
import com.cinecraze.android.adapters.ServerAdapter;
import com.cinecraze.android.adapters.ServerDropdownAdapter;

import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.models.ServerConfig;
import com.cinecraze.android.services.AutoEmbedService;
import com.cinecraze.android.utils.DataManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Button;
import android.app.AlertDialog;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.content.Intent;
import com.cinecraze.android.PreviewActivity;

public class AutoEmbedFragment extends Fragment implements ServerAdapter.OnServerChangeListener {

    // Server Management
    private Button toggleAllServers;
    private Button enableRecommended;
    private Button checkProviderStatus;
    private Button applyAutoEmbed;

    // Server List
    private RecyclerView serversList;
    private ServerAdapter serverAdapter;
    private List<ServerConfig> serverConfigs;
    
    // Server Dropdown
    private AutoCompleteTextView serversDropdown;
    private ServerDropdownAdapter serverDropdownAdapter;
    private TextView selectedServersSummary;

    // Content Management
    private AutoCompleteTextView contentFilter;
    private Button autoGenerateMissing;
    private Button refreshContent;
    private TextView contentSummary;
    private RecyclerView contentList;
    private FloatingActionButton fabOpenPreview;


    // Status and Progress
    private MaterialCardView statusSection;
    private TextView processingStatus;
    private ProgressBar processingProgress;
    private TextView processingDetails;

    private DataManager dataManager;
    private AutoEmbedService autoEmbedService;
    private ExecutorService executor;
    private boolean isProcessing = false;
    private int totalItems = 0;
    private int processedItems = 0;

    private static final String TAG = "AutoEmbedFragment";
    
    // Pagination constants
    private static final int ITEMS_PER_PAGE = 20;
    private static final int MAX_ITEMS_WITHOUT_PAGINATION = 50;
    
    // Pagination UI components
    private LinearLayout paginationControls;
    private TextView paginationInfo;
    private com.google.android.material.button.MaterialButton btnFirstPage;
    private com.google.android.material.button.MaterialButton btnPrevPage;
    private com.google.android.material.button.MaterialButton btnNextPage;
    private com.google.android.material.button.MaterialButton btnLastPage;
    
    // Pagination state
    private List<Object> allDisplayItems = new ArrayList<>();
    private List<Object> currentPageItems = new ArrayList<>();
    private int currentPage = 0;
    private int totalPages = 0;
    private boolean isPaginationEnabled = false;
    
    // Filter state tracking
    private String currentFilter = "All Content";


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auto_embed, container, false);
        
        initializeViews(view);
        setupListeners();
        initializeServices();
        loadServerConfigs();
        loadContentItems();
        
        // Setup dropdowns immediately after initialization
        setupDropdowns();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Reload persisted server configs to prevent reset on tab switch
        List<ServerConfig> persisted = DataManager.getInstance(requireContext()).getAllServerConfigs();
        if (persisted != null && !persisted.isEmpty()) {
            serverConfigs.clear();
            serverConfigs.addAll(persisted);
            serverAdapter.notifyDataSetChanged();
            if (serverDropdownAdapter != null) {
                serverDropdownAdapter.updateServers(serverConfigs);
            }
            updateSelectedServersSummary();
        }
        
        // Setup dropdowns first to ensure they're properly initialized
        setupDropdowns();
        
        // Reload content with current filter
        loadContentItems();
        if (!"All Content".equals(currentFilter)) {
            filterContentByType(currentFilter);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private void initializeViews(View view) {
        // Server Management
        toggleAllServers = view.findViewById(R.id.toggle_all_servers);
        enableRecommended = view.findViewById(R.id.enable_recommended);
        checkProviderStatus = view.findViewById(R.id.check_provider_status);
        applyAutoEmbed = view.findViewById(R.id.apply_auto_embed);

        // Server List
        serversList = view.findViewById(R.id.servers_list);
        
        // Server Dropdown
        serversDropdown = view.findViewById(R.id.servers_dropdown);
        selectedServersSummary = view.findViewById(R.id.selected_servers_summary);

        // Content Management
        contentFilter = view.findViewById(R.id.content_filter);
        autoGenerateMissing = view.findViewById(R.id.auto_generate_missing);
        refreshContent = view.findViewById(R.id.refresh_content);
        contentSummary = view.findViewById(R.id.content_summary);
        contentList = view.findViewById(R.id.content_list);
        fabOpenPreview = view.findViewById(R.id.fab_open_preview);


        // Status and Progress
        statusSection = view.findViewById(R.id.status_section);
        processingStatus = view.findViewById(R.id.processing_status);
        processingProgress = view.findViewById(R.id.processing_progress);
        processingDetails = view.findViewById(R.id.processing_details);

        // Pagination UI components
        paginationControls = view.findViewById(R.id.pagination_controls);
        paginationInfo = view.findViewById(R.id.pagination_info);
        btnFirstPage = view.findViewById(R.id.btn_first_page);
        btnPrevPage = view.findViewById(R.id.btn_prev_page);
        btnNextPage = view.findViewById(R.id.btn_next_page);
        btnLastPage = view.findViewById(R.id.btn_last_page);
    }

    private void setupListeners() {
        toggleAllServers.setOnClickListener(v -> toggleAllServers());
        enableRecommended.setOnClickListener(v -> enableRecommendedServers());
        checkProviderStatus.setOnClickListener(v -> checkProviderStatus());
        applyAutoEmbed.setOnClickListener(v -> applyAutoEmbedToContent());
        autoGenerateMissing.setOnClickListener(v -> autoGenerateMissingServers());
        refreshContent.setOnClickListener(v -> {
            refreshContentSummary();
            loadContentItems();
        });
        if (fabOpenPreview != null) {
            fabOpenPreview.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), PreviewActivity.class);
                startActivity(intent);
            });
        }
        

        
        // Pagination button listeners
        btnFirstPage.setOnClickListener(v -> goToFirstPage());
        btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        btnNextPage.setOnClickListener(v -> goToNextPage());
        btnLastPage.setOnClickListener(v -> goToLastPage());
    }

    private void setupDropdowns() {
        // Content filter dropdown
        String[] contentTypes = {
            "All Content", "Movies", "TV Series", "Live TV", 
            "Missing Servers", "Regional Content", "Recent Additions"
        };
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, contentTypes);
        contentFilter.setAdapter(filterAdapter);
        
        contentFilter.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFilter = contentTypes[position];
            currentFilter = selectedFilter;
            filterContentByType(selectedFilter);
        });
    }

    private void initializeServices() {
        dataManager = DataManager.getInstance(requireContext());
        autoEmbedService = new AutoEmbedService();
        executor = Executors.newFixedThreadPool(3);
        
        serverConfigs = new ArrayList<>();
        serverAdapter = new ServerAdapter(serverConfigs, this);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        serversList.setLayoutManager(layoutManager);
        serversList.setAdapter(serverAdapter);

        // Initialize server dropdown
        serverDropdownAdapter = new ServerDropdownAdapter(requireContext(), serverConfigs);
        serversDropdown.setAdapter(serverDropdownAdapter);
        
        // Enable dropdown functionality
        serversDropdown.setFocusable(false);
        serversDropdown.setClickable(true);
        
        // Add click listener to ensure dropdown shows
        serversDropdown.setOnClickListener(v -> {
            serversDropdown.showDropDown();
        });
        
        // Add touch listener as backup
        serversDropdown.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                serversDropdown.showDropDown();
                return true;
            }
            return false;
        });
        
        // Set dropdown listener
        serverDropdownAdapter.setOnServerSelectionListener(new ServerDropdownAdapter.OnServerSelectionListener() {
            @Override
            public void onServerSelectionChanged(ServerConfig server, boolean selected) {
                // Update the server configuration
                        server.setEnabled(selected);
        dataManager.saveServerConfigs(serverConfigs);
        if (serverDropdownAdapter != null) serverDropdownAdapter.updateServers(serverConfigs);
        updateSelectedServersSummary();
        showStatus("Server " + server.getName() + " " + (selected ? "enabled" : "disabled"));
            }
            
            @Override
            public void onSelectionChanged(List<ServerConfig> selectedServers) {
                // Update the summary text
                updateSelectedServersSummary();
            }
        });

        contentList.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void loadServerConfigs() {
        // Load persisted configs first
        List<ServerConfig> persisted = DataManager.getInstance(requireContext()).getAllServerConfigs();
        serverConfigs.clear();
        if (persisted != null && !persisted.isEmpty()) {
            serverConfigs.addAll(persisted);
            serverAdapter.notifyDataSetChanged();
            if (serverDropdownAdapter != null) {
                serverDropdownAdapter.updateServers(serverConfigs);
                updateSelectedServersSummary();
            }
            updateContentSummary();
            return;
        }
        
        // Seed defaults on first run
        // Primary servers (high reliability)
        serverConfigs.add(new ServerConfig("VidSrc", "https://vidsrc.to/embed/{title}", true, "High"));
        serverConfigs.add(new ServerConfig("VidSrcME", "https://vidsrc.me/embed/{title}", true, "High"));
        serverConfigs.add(new ServerConfig("VidSrcTO", "https://vidsrc.to/embed/{title}", true, "High"));
        serverConfigs.add(new ServerConfig("EmbedSU", "https://embed.su/embed/{title}", true, "High"));
        serverConfigs.add(new ServerConfig("VidJoy", "https://vidjoy.pro/embed/{title}", true, "High"));
        
        // Secondary servers (good reliability)
        serverConfigs.add(new ServerConfig("MultiEmbed", "https://multiembed.mov/directstream.php?video_id={title}", true, "Medium"));
        serverConfigs.add(new ServerConfig("FlixHQ", "https://flixhq.to/watch/{title}", true, "Medium"));
        serverConfigs.add(new ServerConfig("HDToday", "https://hdtoday.tv/embed/{title}", true, "Medium"));
        serverConfigs.add(new ServerConfig("VidCloud", "https://vidcloud.to/embed/{title}", true, "Medium"));
        
        // Additional servers
        serverConfigs.add(new ServerConfig("StreamWish", "https://streamwish.to/e/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("MixDrop", "https://mixdrop.co/e/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("FileMoon", "https://filemoon.sx/e/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("VidLink", "https://vidlink.pro/movie/{title}", false, "Medium"));
        
        // Backup servers
        serverConfigs.add(new ServerConfig("StreamLare", "https://streamlare.com/e/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("StreamHub", "https://streamhub.to/e/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("DoodStream", "https://doodstream.com/e/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("UpStream", "https://upstream.to/{title}", false, "Low"));
        
        // Alternative providers
        serverConfigs.add(new ServerConfig("StreamTape", "https://streamtape.com/e/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("GoDrivePlayer", "https://godriveplayer.com/embed/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("TwoTwoEmbed", "https://2embed.cc/embed/{title}", false, "Low"));
        serverConfigs.add(new ServerConfig("EmbedSoap", "https://www.embedsoap.com/embed/{title}", false, "Low"));
        
        // Regional servers
        serverConfigs.add(new ServerConfig("NontonFilm", "https://tv.nontonguru.info/embed/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("GoMovies", "https://gomovies.sx/watch/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("ShowBox", "https://www.showbox.media/embed/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("PrimeWire", "https://primewire.mx/embed/{title}", false, "Medium"));
        serverConfigs.add(new ServerConfig("Cataz", "https://cataz.net/embed/{title}", false, "Medium"));
        
        serverAdapter.notifyDataSetChanged();
        
        // Persist defaults on first run
        DataManager.getInstance(requireContext()).saveServerConfigs(serverConfigs);
        
        // Update dropdown adapter
        if (serverDropdownAdapter != null) {
            serverDropdownAdapter.updateServers(serverConfigs);
            updateSelectedServersSummary();
        }
        
        updateContentSummary();
    }

    private void loadContentItems() {
        List<ContentItem> all = dataManager.getAllContent();
        List<ContentItem> nonSeriesOrMovies = new ArrayList<>();
        // Group TV series by series title (now that import creates proper structure)
        java.util.Map<String, List<ContentItem>> seriesGroups = new java.util.HashMap<>();
        for (ContentItem item : all) {
            if ("TV Series".equals(item.getType())) {
                String seriesTitle = item.getSeriesTitle();
                if (seriesTitle == null || seriesTitle.trim().isEmpty()) {
                    // Fallback to extracting from title for backward compatibility
                    seriesTitle = extractSeriesTitle(item.getTitle());
                }
                Log.d(TAG, "Grouping episode: '" + item.getTitle() + "' → Series: '" + seriesTitle + "'");
                seriesGroups.computeIfAbsent(seriesTitle, k -> new ArrayList<>()).add(item);
            } else {
                nonSeriesOrMovies.add(item);
            }
        }
        
        // Build a flattened list: one entry per series group, plus movies/live tv
        allDisplayItems.clear();
        for (java.util.Map.Entry<String, List<ContentItem>> e : seriesGroups.entrySet()) {
            String seriesTitle = e.getKey();
            List<ContentItem> eps = e.getValue();
            int seasons = (int) eps.stream().map(it -> it.getSeason() == null ? 1 : it.getSeason()).distinct().count();
            int episodes = eps.size();
            String poster = eps.get(0).getImageUrl();
            Log.d(TAG, "Creating series group: '" + seriesTitle + "' with " + episodes + " episodes");
            allDisplayItems.add(new SeriesGroup(seriesTitle, seasons, episodes, poster, eps));
        }
        // Append non-series items as-is
        allDisplayItems.addAll(nonSeriesOrMovies);
        
        // Determine if pagination is needed
        isPaginationEnabled = allDisplayItems.size() > MAX_ITEMS_WITHOUT_PAGINATION;
        
        if (isPaginationEnabled) {
            totalPages = (int) Math.ceil((double) allDisplayItems.size() / ITEMS_PER_PAGE);
            currentPage = 0;
            Log.d(TAG, "Pagination enabled: " + allDisplayItems.size() + " total items, " + totalPages + " pages");
            updateCurrentPage();
        } else {
            // No pagination needed, show all items
            currentPageItems = new ArrayList<>(allDisplayItems);
            Log.d(TAG, "No pagination: " + allDisplayItems.size() + " items");
            updateAdapter();
        }
    }
    
    private String extractSeriesTitle(String episodeTitle) {
        if (episodeTitle == null || episodeTitle.trim().isEmpty()) {
            return "Unknown Series";
        }
        
        String originalTitle = episodeTitle.trim();
        String title = originalTitle;
        
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
        
        String result = title.trim();
        
        // Debug logging to see what's happening
        if (!originalTitle.equals(result)) {
            Log.d(TAG, "Series extraction: '" + originalTitle + "' → '" + result + "'");
        }
        
        return result;
    }
    
    private void updateCurrentPage() {
        if (!isPaginationEnabled) {
            currentPageItems = new ArrayList<>(allDisplayItems);
            updateAdapter();
            return;
        }
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allDisplayItems.size());
        
        currentPageItems.clear();
        if (startIndex < allDisplayItems.size()) {
            currentPageItems.addAll(allDisplayItems.subList(startIndex, endIndex));
        }
        
        Log.d(TAG, "Page " + (currentPage + 1) + "/" + totalPages + " showing items " + (startIndex + 1) + "-" + endIndex);
        updateAdapter();
        updatePaginationStatus();
    }
    
    private void updateAdapter() {
        contentList.setAdapter(new GroupedContentAdapter(currentPageItems, new GroupedContentAdapter.GroupActionListener() {
            @Override public void onEditSeries(SeriesGroup group) { showEditSeriesDialog(group); }
            @Override public void onDeleteSeries(SeriesGroup group) { deleteSeriesGroup(group); }
            @Override public void onAddServerToSeries(SeriesGroup group) { addServerToSeries(group); }
            @Override public void onEditSingle(ContentItem item) { showEditDialog(item); }
            @Override public void onDeleteSingle(ContentItem item) {             dataManager.removeContentItem(item); 
            
            // Reload content and reapply current filter
            loadContentItems();
            if (!"All Content".equals(currentFilter)) {
                filterContentByType(currentFilter);
            }
            refreshContentSummary(); }
        }));
    }
    
    private void updatePaginationStatus() {
        if (!isPaginationEnabled) {
            paginationControls.setVisibility(View.GONE);
            return;
        }
        
        paginationControls.setVisibility(View.VISIBLE);
        
        String paginationText = "Page " + (currentPage + 1) + " of " + totalPages + 
                               " (" + currentPageItems.size() + "/" + allDisplayItems.size() + " items)";
        paginationInfo.setText(paginationText);
        
        // Update button states
        btnFirstPage.setEnabled(currentPage > 0);
        btnPrevPage.setEnabled(currentPage > 0);
        btnNextPage.setEnabled(currentPage < totalPages - 1);
        btnLastPage.setEnabled(currentPage < totalPages - 1);
        
        // Update button opacity for visual feedback
        btnFirstPage.setAlpha(currentPage > 0 ? 1.0f : 0.5f);
        btnPrevPage.setAlpha(currentPage > 0 ? 1.0f : 0.5f);
        btnNextPage.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.5f);
        btnLastPage.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.5f);
    }
    
    private void goToFirstPage() {
        if (isPaginationEnabled && currentPage > 0) {
            currentPage = 0;
            updateCurrentPage();
        }
    }
    
    private void goToPreviousPage() {
        if (isPaginationEnabled && currentPage > 0) {
            currentPage--;
            updateCurrentPage();
        }
    }
    
    private void goToNextPage() {
        if (isPaginationEnabled && currentPage < totalPages - 1) {
            currentPage++;
            updateCurrentPage();
        }
    }
    
    private void goToLastPage() {
        if (isPaginationEnabled && totalPages > 0) {
            currentPage = totalPages - 1;
            updateCurrentPage();
        }
    }
    
    // Restore the SeriesGroup class
    static class SeriesGroup {
        final String seriesTitle;
        final int seasons;
        final int episodesCount;
        final String posterUrl;
        final List<ContentItem> episodes;
        
        SeriesGroup(String title, int seasons, int episodesCount, String poster, List<ContentItem> episodes) {
            this.seriesTitle = title;
            this.seasons = seasons;
            this.episodesCount = episodesCount;
            this.posterUrl = poster;
            this.episodes = episodes;
        }
    }
    
    private void updateContentSummary() {
        List<ContentItem> allContent = dataManager.getAllContent();
        
        int totalItems = allContent.size();
        int movies = 0, series = 0, liveTv = 0;
        int itemsWithServers = 0;
        
        for (ContentItem item : allContent) {
            switch (item.getType()) {
                case "Movie":
                    movies++;
                    break;
                case "TV Series":
                    series++;
                    break;
                case "Live TV":
                    liveTv++;
                    break;
            }
            
            if (item.getServers() != null && !item.getServers().isEmpty()) {
                itemsWithServers++;
            }
        }
        
        String summary = String.format(
            "📊 Content Summary\n\n" +
            "Total Items: %d\n" +
            "Movies: %d\n" +
            "TV Series: %d\n" +
            "Live TV: %d\n" +
            "With Servers: %d\n" +
            "Missing Servers: %d",
            totalItems, movies, series, liveTv, itemsWithServers, (totalItems - itemsWithServers)
        );
        
        contentSummary.setText(summary);
    }

    private void filterContentByType(String filterType) {
        Log.d("AutoEmbed", "Filtering content by: " + filterType);
        
        List<ContentItem> all = dataManager.getAllContent();
        List<ContentItem> filteredNonSeries = new ArrayList<>();
        java.util.Map<String, List<ContentItem>> filteredSeriesGroups = new java.util.HashMap<>();
        
        // Apply filtering based on selected type
        for (ContentItem item : all) {
            boolean includeItem = false;
            
            switch (filterType) {
                case "All Content":
                    includeItem = true;
                    break;
                case "Movies":
                    includeItem = "Movie".equals(item.getType());
                    break;
                case "TV Series":
                    includeItem = "TV Series".equals(item.getType());
                    break;
                case "Live TV":
                    includeItem = "Live TV".equals(item.getType());
                    break;
                case "Missing Servers":
                    includeItem = item.getServers() == null || item.getServers().isEmpty();
                    break;
                case "Regional Content":
                    includeItem = item.getCountry() != null && !item.getCountry().isEmpty();
                    break;
                case "Recent Additions":
                    // Items added in last 7 days
                    includeItem = item.getCreatedAt() != null && 
                        (System.currentTimeMillis() - item.getCreatedAt()) < (7 * 24 * 60 * 60 * 1000L);
                    break;
                default:
                    includeItem = true;
                    break;
            }
            
            if (includeItem) {
                if ("TV Series".equals(item.getType())) {
                    // Group TV series by base title using simple title extraction
                    String seriesTitle = extractSeriesTitle(item.getTitle());
                    filteredSeriesGroups.computeIfAbsent(seriesTitle, k -> new ArrayList<>()).add(item);
                } else {
                    // Individual items (movies, live TV, etc.)
                    filteredNonSeries.add(item);
                }
            }
        }
        
        // Build filtered display list
        allDisplayItems.clear();
        
        // Add filtered series groups
        for (java.util.Map.Entry<String, List<ContentItem>> e : filteredSeriesGroups.entrySet()) {
            String seriesTitle = e.getKey();
            List<ContentItem> eps = e.getValue();
            int seasons = (int) eps.stream().map(it -> it.getSeason() == null ? 1 : it.getSeason()).distinct().count();
            int episodes = eps.size();
            String poster = eps.get(0).getImageUrl();
            allDisplayItems.add(new SeriesGroup(seriesTitle, seasons, episodes, poster, eps));
        }
        
        // Add filtered individual items
        allDisplayItems.addAll(filteredNonSeries);
        
        // Reset pagination for filtered results
        isPaginationEnabled = allDisplayItems.size() > MAX_ITEMS_WITHOUT_PAGINATION;
        currentPage = 0;
        
        if (isPaginationEnabled) {
            totalPages = (int) Math.ceil((double) allDisplayItems.size() / ITEMS_PER_PAGE);
            Log.d(TAG, "Filter applied with pagination: " + allDisplayItems.size() + " items, " + totalPages + " pages");
            updateCurrentPage();
        } else {
            currentPageItems = new ArrayList<>(allDisplayItems);
            Log.d(TAG, "Filter applied without pagination: " + allDisplayItems.size() + " items");
            updateAdapter();
        }
        
        // Update content summary
        updateContentSummary();
        
        // Show status with count
        int totalFiltered = allDisplayItems.size();
        String statusMessage = "Filtered by: " + filterType;
        if (totalFiltered > 0) {
            statusMessage += " (" + totalFiltered + " items)";
            if (isPaginationEnabled) {
                statusMessage += " - Page 1 of " + totalPages;
            }
        } else {
            statusMessage += " (no items found)";
        }
        showStatus(statusMessage);
    }

    private void refreshContentSummary() {
        updateContentSummary();
        showStatus("Content summary refreshed");
    }

    private void toggleAllServers() {
        boolean allEnabled = serverConfigs.stream().allMatch(ServerConfig::isEnabled);
        boolean newState = !allEnabled;
        
        for (ServerConfig config : serverConfigs) {
            config.setEnabled(newState);
        }
        
        // Persist
        dataManager.saveServerConfigs(serverConfigs);
        serverAdapter.notifyDataSetChanged();
        
        // Update dropdown adapter
        if (serverDropdownAdapter != null) {
            serverDropdownAdapter.updateServers(serverConfigs);
            updateSelectedServersSummary();
        }
        
        showStatus(newState ? "All servers enabled" : "All servers disabled");
    }

    private void enableRecommendedServers() {
        // Disable all first
        for (ServerConfig config : serverConfigs) {
            config.setEnabled(false);
        }
        
        // Enable recommended servers (high and medium reliability)
        String[] recommendedServers = {
            "VidSrc", "VidSrcME", "VidSrcTO", "EmbedSU", "VidJoy",
            "MultiEmbed", "FlixHQ", "HDToday", "VidCloud",
            "StreamWish", "MixDrop", "FileMoon", "VidLink"
        };
        
        for (String serverName : recommendedServers) {
            for (ServerConfig config : serverConfigs) {
                if (config.getName().equals(serverName)) {
                    config.setEnabled(true);
                    break;
                }
            }
        }
        
        // Persist choices
        dataManager.saveServerConfigs(serverConfigs);
        
        serverAdapter.notifyDataSetChanged();
        
        // Update dropdown adapter
        if (serverDropdownAdapter != null) {
            serverDropdownAdapter.updateServers(serverConfigs);
            updateSelectedServersSummary();
        }
        
        showStatus("Recommended servers enabled");
    }

    private void autoGenerateMissingServers() {
        if (isProcessing) {
            showStatus("Already processing, please wait...");
            return;
        }
        
        List<ContentItem> contentItems = dataManager.getAllContent();
        List<ContentItem> itemsWithMissingServers = new ArrayList<>();
        
        for (ContentItem item : contentItems) {
            if (item.getServers() == null || item.getServers().isEmpty()) {
                itemsWithMissingServers.add(item);
            }
        }
        
        if (itemsWithMissingServers.isEmpty()) {
            showStatus("No content with missing servers found");
            return;
        }
        
        isProcessing = true;
        totalItems = itemsWithMissingServers.size();
        processedItems = 0;
        
        processingStatus.setText("Generating missing servers...");
        processingProgress.setMax(totalItems);
        processingProgress.setProgress(0);
        statusSection.setVisibility(View.VISIBLE);
        
        executor.submit(() -> {
            for (ContentItem item : itemsWithMissingServers) {
                if (!isProcessing) break;
                
                List<String> newServers = generateServersForContent(item);
                item.setServers(newServers);
                dataManager.updateContent(item);
                
                processedItems++;
                final int currentProcessed = processedItems;
                
                requireActivity().runOnUiThread(() -> {
                    processingProgress.setProgress(currentProcessed);
                    processingDetails.setText(String.format("Generated servers for %d/%d items", currentProcessed, totalItems));
                });
            }
            
            requireActivity().runOnUiThread(() -> {
                isProcessing = false;
                processingStatus.setText("Server generation complete!");
                processingProgress.setProgress(totalItems);
                
                // Hide status after 3 seconds
                statusSection.postDelayed(() -> statusSection.setVisibility(View.GONE), 3000);
                
                updateContentSummary();
                showStatus("Generated servers for " + totalItems + " items");
            });
        });
    }

    private List<String> generateServersForContent(ContentItem item) {
        List<String> servers = new ArrayList<>();
        String title = item.getTitle();
        String encodedTitle = title.replace(" ", "%20");
        
        for (ServerConfig config : serverConfigs) {
            if (config.isEnabled()) {
                String serverUrl = config.getUrl().replace("{title}", encodedTitle);
                servers.add(config.getName() + " 1080p|" + serverUrl);
            }
        }
        
        // Preserve existing DRM servers if any
        if (item.getServers() != null) {
            for (String existingServer : item.getServers()) {
                if (existingServer.contains("|drm:")) {
                    // This is a DRM server, preserve it
                    servers.add(existingServer);
                }
            }
        }
        
        return servers;
    }

    private void checkProviderStatus() {
        checkProviderStatus(false);
    }
    
    private void checkProviderStatus(boolean forceRefresh) {
        if (isProcessing) {
            showStatus("Already processing, please wait...");
            return;
        }
        
        isProcessing = true;
        processingStatus.setText(forceRefresh ? "Force checking provider status..." : "Checking provider status...");
        processingProgress.setIndeterminate(true);
        statusSection.setVisibility(View.VISIBLE);
        
        // Clear cache if force refresh
        if (forceRefresh) {
            autoEmbedService.clearStatusCache();
        }
        
        executor.submit(() -> {
            int totalServers = serverConfigs.size();
            int checkedServers = 0;
            int workingServers = 0;
            
            for (ServerConfig config : serverConfigs) {
                if (config.isEnabled()) {
                    boolean isWorking = autoEmbedService.checkServerStatus(config.getName());
                    config.setWorking(isWorking);
                    if (isWorking) workingServers++;
                    checkedServers++;
                    
                    final int currentChecked = checkedServers;
                    final int currentWorking = workingServers;
                    
                    requireActivity().runOnUiThread(() -> {
                        processingDetails.setText(String.format("Checked %d/%d servers (%d working)", 
                            currentChecked, totalServers, currentWorking));
                    });
                }
            }
            
            // Create final copies for the lambda expression
            final int finalWorkingServers = workingServers;
            final int finalCheckedServers = checkedServers;
            
            requireActivity().runOnUiThread(() -> {
                isProcessing = false;
                processingStatus.setText(String.format("Status check complete: %d/%d servers working", 
                    finalWorkingServers, finalCheckedServers));
                processingProgress.setIndeterminate(false);
                processingProgress.setProgress(100);
                serverAdapter.notifyDataSetChanged();
                
                // Update dropdown adapter
                if (serverDropdownAdapter != null) {
                    serverDropdownAdapter.updateServers(serverConfigs);
                }
                
                // Hide status after 3 seconds
                statusSection.postDelayed(() -> statusSection.setVisibility(View.GONE), 3000);
            });
        });
    }

    private void applyAutoEmbedToContent() {
        if (isProcessing) {
            showStatus("Already processing, please wait...");
            return;
        }
        
        List<ContentItem> contentItems = dataManager.getAllContent();
        if (contentItems.isEmpty()) {
            showStatus("No content to process");
            return;
        }
        
        isProcessing = true;
        totalItems = contentItems.size();
        processedItems = 0;
        
        processingStatus.setText("Applying auto-embed servers...");
        processingProgress.setMax(totalItems);
        processingProgress.setProgress(0);
        statusSection.setVisibility(View.VISIBLE);
        
        executor.submit(() -> {
            for (ContentItem item : contentItems) {
                if (!isProcessing) break; // Allow cancellation
                
                List<String> newServers = generateServersForContent(item);
                item.setServers(newServers);
                dataManager.updateContent(item);
                
                processedItems++;
                final int currentProcessed = processedItems;
                
                requireActivity().runOnUiThread(() -> {
                    processingProgress.setProgress(currentProcessed);
                    processingDetails.setText(String.format("Processed %d/%d items", currentProcessed, totalItems));
                });
            }
            
            requireActivity().runOnUiThread(() -> {
                isProcessing = false;
                processingStatus.setText("Auto-embed complete!");
                processingProgress.setProgress(totalItems);
                
                // Hide status after 3 seconds
                statusSection.postDelayed(() -> statusSection.setVisibility(View.GONE), 3000);
                
                updateContentSummary();
                
                // Reload content and reapply current filter
                loadContentItems();
                if (!"All Content".equals(currentFilter)) {
                    filterContentByType(currentFilter);
                }
                showStatus("Auto-embed applied to " + totalItems + " items");
            });
        });
    }

    private void showEditDialog(ContentItem originalItem) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_content, null);
        dialog.setContentView(sheet);

        TextInputEditText etTitle = sheet.findViewById(R.id.edit_title);
        TextInputEditText etDescription = sheet.findViewById(R.id.edit_description);
        TextInputEditText etPoster = sheet.findViewById(R.id.edit_poster);
        TextInputEditText etYear = sheet.findViewById(R.id.edit_year);
        TextInputEditText etRating = sheet.findViewById(R.id.edit_rating);
        LinearLayout serversContainer = sheet.findViewById(R.id.servers_container);
        Button btnAddServer = sheet.findViewById(R.id.btn_add_server);
        Button btnSave = sheet.findViewById(R.id.btn_save);
        Button btnCancel = sheet.findViewById(R.id.btn_cancel);

        // Prefill
        etTitle.setText(originalItem.getTitle());
        etDescription.setText(originalItem.getDescription());
        etPoster.setText(originalItem.getImageUrl());
        etYear.setText(originalItem.getYear() != null ? String.valueOf(originalItem.getYear()) : "");
        etRating.setText(originalItem.getRating() != null ? String.valueOf(originalItem.getRating()) : "");

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (originalItem.getServers() != null) {
            for (String s : originalItem.getServers()) {
                String[] parts = s.split("\\|", 3);
                String name = parts.length > 0 ? parts[0] : "";
                String url = parts.length > 1 ? parts[1] : "";
                View row = inflateServerRow(inflater, serversContainer, name, url);
                serversContainer.addView(row);
            }
        }

        btnAddServer.setOnClickListener(v -> {
            View row = inflateServerRow(inflater, serversContainer, "", "");
            serversContainer.addView(row);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            // Build updated item
            // Mutate original item to preserve identifier and equality
            originalItem.setTitle(getTextOr(etTitle, originalItem.getTitle()));
            originalItem.setDescription(getTextOr(etDescription, originalItem.getDescription()));
            originalItem.setImageUrl(getTextOr(etPoster, originalItem.getImageUrl()));
            originalItem.setYear(parseIntOr(etYear, originalItem.getYear()));
            originalItem.setRating(parseDoubleOr(etRating, originalItem.getRating()));

            // Collect servers
            List<String> newServers = new ArrayList<>();
            for (int i = 0; i < serversContainer.getChildCount(); i++) {
                View child = serversContainer.getChildAt(i);
                TextInputEditText nameEt = child.findViewById(R.id.server_name);
                TextInputEditText urlEt = child.findViewById(R.id.server_url);
                String name = nameEt != null ? String.valueOf(nameEt.getText()).trim() : "";
                String url = urlEt != null ? String.valueOf(urlEt.getText()).trim() : "";
                if (!name.isEmpty() && !url.isEmpty()) {
                    newServers.add(name + "|" + url);
                }
            }
            originalItem.setServers(newServers);

            // Update via DataManager
            dataManager.updateContent(originalItem);
            showStatus("Item updated");
            dialog.dismiss();
            
            // Reload content and reapply current filter
            loadContentItems();
            if (!"All Content".equals(currentFilter)) {
                filterContentByType(currentFilter);
            }
            refreshContentSummary();
        });

        dialog.show();
    }

    private void showEditSeriesDialog(SeriesGroup group) {
        Log.d(TAG, "Opening series editor for: '" + group.seriesTitle + "' with " + group.episodes.size() + " episodes");
        
        // Debug: Log all episodes in this group to verify they belong to the same series
        for (ContentItem episode : group.episodes) {
            Log.d(TAG, "Episode in group: '" + episode.getTitle() + "' S" + episode.getSeason() + "E" + episode.getEpisode());
        }
        
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_edit_series, null);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create();
            
        // Initialize dialog views
        TextInputEditText etTitle = dialogView.findViewById(R.id.series_title_input);
        TextInputEditText etDescription = dialogView.findViewById(R.id.series_description_input);
        TextInputEditText etPoster = dialogView.findViewById(R.id.series_poster_input);
        TextInputEditText etYear = dialogView.findViewById(R.id.series_year_input);
        TextInputEditText etRating = dialogView.findViewById(R.id.series_rating_input);
        RecyclerView tree = dialogView.findViewById(R.id.series_tree_list);
        Button save = dialogView.findViewById(R.id.btn_series_save);
        Button cancel = dialogView.findViewById(R.id.btn_series_cancel);
        
        // Populate with current data
        ContentItem first = group.episodes.get(0);
        etTitle.setText(group.seriesTitle);
        etDescription.setText(first.getDescription());
        etPoster.setText(group.posterUrl);
        etYear.setText(first.getYear() != null ? String.valueOf(first.getYear()) : "");
        etRating.setText(first.getRating() != null ? String.valueOf(first.getRating()) : "");
        
        // Build tree structure
        List<Object> nodes = new ArrayList<>();
        Map<Integer, List<ContentItem>> seasonMap = new HashMap<>();
        for (ContentItem ep : group.episodes) {
            int s = ep.getSeason() == null ? 1 : ep.getSeason();
            seasonMap.computeIfAbsent(s, k -> new ArrayList<>()).add(ep);
        }
        
        List<Integer> seasons = new ArrayList<>(seasonMap.keySet());
        Collections.sort(seasons);
        for (Integer season : seasons) {
            nodes.add("Season " + season);
            List<ContentItem> eps = seasonMap.get(season);
            eps.sort((a, b) -> Integer.compare(
                a.getEpisode() == null ? 1 : a.getEpisode(),
                b.getEpisode() == null ? 1 : b.getEpisode()
            ));
            nodes.addAll(eps);
        }
        
        tree.setLayoutManager(new LinearLayoutManager(requireContext()));
        tree.setAdapter(new SeriesTreeAdapter(nodes, new SeriesTreeAdapter.EpisodeActionListener() {
            @Override public void onOpenEpisodeEditor(ContentItem ep) { showEditDialog(ep); }
            @Override public void onDeleteEpisode(ContentItem ep) { dataManager.removeContentItem(ep); }
        }));
        
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String newTitle = getTextOr(etTitle, group.seriesTitle);
            String newDesc = getTextOr(etDescription, first.getDescription());
            String newPoster = getTextOr(etPoster, group.posterUrl);
            Integer newYear = parseIntOr(etYear, first.getYear());
            Double newRating = parseDoubleOr(etRating, first.getRating());
            for (ContentItem ep : group.episodes) {
                ep.setTitle(ep.getTitle().replaceFirst("^" + java.util.regex.Pattern.quote(group.seriesTitle), newTitle));
                ep.setDescription(newDesc);
                ep.setImageUrl(newPoster);
                ep.setYear(newYear);
                ep.setRating(newRating);
                dataManager.updateContent(ep);
            }
            showStatus("Series updated");
            dialog.dismiss();
            
            // Reload content and reapply current filter
            loadContentItems();
            if (!"All Content".equals(currentFilter)) {
                filterContentByType(currentFilter);
            }
            refreshContentSummary();
        });
        dialog.show();
    }

    private void deleteSeriesGroup(SeriesGroup group) {
        for (ContentItem ep : group.episodes) dataManager.removeContentItem(ep);
        showStatus("Series deleted");
        
        // Reload content and reapply current filter
        loadContentItems();
        if (!"All Content".equals(currentFilter)) {
            filterContentByType(currentFilter);
        }
        refreshContentSummary();
    }

    private void addServerToSeries(SeriesGroup group) {
        for (ContentItem ep : group.episodes) {
            ep.addServer("Custom 1080p|https://example.com/embed/");
            dataManager.updateContent(ep);
        }
        showStatus("Server added to series");
        
        // Reload content and reapply current filter
        loadContentItems();
        if (!"All Content".equals(currentFilter)) {
            filterContentByType(currentFilter);
        }
    }

    // Adapter supporting mixed items (series groups and single items)
    static class GroupedContentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface GroupActionListener {
            void onEditSeries(SeriesGroup group);
            void onDeleteSeries(SeriesGroup group);
            void onAddServerToSeries(SeriesGroup group);
            void onEditSingle(ContentItem item);
            void onDeleteSingle(ContentItem item);
        }
        private static final int TYPE_SERIES = 1;
        private static final int TYPE_SINGLE = 2;
        private final List<Object> items;
        private final GroupActionListener listener;
        GroupedContentAdapter(List<Object> items, GroupActionListener listener) { this.items = items; this.listener = listener; }
        @Override public int getItemViewType(int position) { return items.get(position) instanceof SeriesGroup ? TYPE_SERIES : TYPE_SINGLE; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SERIES) {
                View v = inf.inflate(R.layout.item_series_group_with_actions, parent, false);
                return new SeriesVH(v);
            }
            View v = inf.inflate(R.layout.item_content_with_actions, parent, false);
            return new SingleVH(v);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object obj = items.get(position);
            if (holder instanceof SeriesVH) ((SeriesVH) holder).bind((SeriesGroup) obj, listener);
            else ((SingleVH) holder).bind((ContentItem) obj, listener);
        }
        @Override public int getItemCount() { return items.size(); }
        static class SeriesVH extends RecyclerView.ViewHolder {
            android.widget.ImageView poster; TextView title; TextView meta; com.google.android.material.button.MaterialButton edit; com.google.android.material.button.MaterialButton delete; com.google.android.material.button.MaterialButton addServer;
            SeriesVH(View itemView) { 
                super(itemView); 
                poster = itemView.findViewById(R.id.series_poster); 
                title = itemView.findViewById(R.id.series_title); 
                meta = itemView.findViewById(R.id.series_meta); 
                edit = itemView.findViewById(R.id.btn_edit_series); 
                delete = itemView.findViewById(R.id.btn_delete_series); 
                addServer = itemView.findViewById(R.id.btn_add_server_all); 
            }
            void bind(SeriesGroup g, GroupActionListener l) {
                title.setText(g.seriesTitle);
                meta.setText("Seasons: " + g.seasons + " • Episodes: " + g.episodesCount);
                String url = g.posterUrl; 
                if (url == null || url.isEmpty()) {
                    poster.setImageResource(R.drawable.ic_tv); 
                } else {
                    com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(url)
                        .placeholder(R.drawable.ic_tv)
                        .error(R.drawable.ic_tv)
                        .centerCrop()
                        .into(poster);
                }
                edit.setOnClickListener(v -> l.onEditSeries(g));
                delete.setOnClickListener(v -> l.onDeleteSeries(g));
                addServer.setOnClickListener(v -> l.onAddServerToSeries(g));
            }
        }
        static class SingleVH extends RecyclerView.ViewHolder {
            android.widget.ImageView poster; TextView title; TextView subtitle; com.google.android.material.button.MaterialButton edit; com.google.android.material.button.MaterialButton delete;
            SingleVH(View itemView) { 
                super(itemView); 
                poster = itemView.findViewById(R.id.content_image); 
                title = itemView.findViewById(R.id.content_title); 
                subtitle = itemView.findViewById(R.id.content_meta); 
                edit = itemView.findViewById(R.id.edit_button); 
                delete = itemView.findViewById(R.id.delete_button); 
            }
            void bind(ContentItem item, GroupActionListener l) {
                title.setText(item.getDisplayTitle());
                subtitle.setText((item.getType() != null ? item.getType() : "") + (item.getYear() != null ? (" • " + item.getYear()) : ""));
                String url = item.getImageUrl(); 
                if (url == null || url.isEmpty()) {
                    poster.setImageResource(R.drawable.ic_movie); 
                } else {
                    com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(url)
                        .placeholder(R.drawable.ic_movie)
                        .error(R.drawable.ic_movie)
                        .centerCrop()
                        .into(poster);
                }
                edit.setOnClickListener(v -> l.onEditSingle(item));
                delete.setOnClickListener(v -> l.onDeleteSingle(item));
            }
        }
    }

    private View inflateServerRow(LayoutInflater inflater, ViewGroup container, String defName, String defUrl) {
        View row = inflater.inflate(R.layout.item_server_input, container, false);
        TextInputEditText name = row.findViewById(R.id.server_name);
        TextInputEditText url = row.findViewById(R.id.server_url);
        Button remove = row.findViewById(R.id.remove_server);
        name.setText(defName);
        url.setText(defUrl);
        remove.setOnClickListener(v -> container.removeView(row));
        return row;
    }

    private String getTextOr(TextInputEditText et, String fallback) {
        String t = et.getText() != null ? et.getText().toString().trim() : "";
        return t.isEmpty() ? fallback : t;
    }

    private Integer parseIntOr(TextInputEditText et, Integer fallback) {
        try {
            String t = et.getText() != null ? et.getText().toString().trim() : "";
            if (t.isEmpty()) return fallback;
            return Integer.parseInt(t);
        } catch (Exception e) { return fallback; }
    }

    private Double parseDoubleOr(TextInputEditText et, Double fallback) {
        try {
            String t = et.getText() != null ? et.getText().toString().trim() : "";
            if (t.isEmpty()) return fallback;
            return Double.parseDouble(t);
        } catch (Exception e) { return fallback; }
    }

    public void onServerConfigChanged(ServerConfig config) {
        // Handle server configuration changes
        dataManager.saveServerConfigs(serverConfigs);
        showStatus("Server configuration updated");
    }

    @Override
    public void onServerEnabledChanged(ServerConfig server, boolean enabled) {
        server.setEnabled(enabled);
        dataManager.saveServerConfigs(serverConfigs);
        if (serverDropdownAdapter != null) serverDropdownAdapter.updateServers(serverConfigs);
        updateSelectedServersSummary();
        showStatus("Server " + server.getName() + " " + (enabled ? "enabled" : "disabled"));
    }

    @Override
    public void onServerQualityChanged(ServerConfig server, String quality) {
        server.setQuality(quality);
        dataManager.saveServerConfigs(serverConfigs);
        showStatus("Server " + server.getName() + " quality updated to " + quality);
    }

    private void updateSelectedServersSummary() {
        if (selectedServersSummary != null && serverDropdownAdapter != null) {
            String summaryText = serverDropdownAdapter.getSelectedServersText();
            selectedServersSummary.setText(summaryText);
            
            // Update dropdown text
            if (serversDropdown != null) {
                serversDropdown.setText(summaryText);
                // Ensure the dropdown is properly configured
                serversDropdown.setFocusable(false);
                serversDropdown.setClickable(true);
            }
        }
    }

    private void showStatus(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Restore the SeriesTreeAdapter for episode management
    static class SeriesTreeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface EpisodeActionListener {
            void onOpenEpisodeEditor(ContentItem ep);
            void onDeleteEpisode(ContentItem ep);
        }
        private static final int TYPE_SEASON_HEADER = 1;
        private static final int TYPE_EPISODE = 2;
        private final List<Object> nodes;
        private final EpisodeActionListener listener;
        
        SeriesTreeAdapter(List<Object> nodes, EpisodeActionListener l) { 
            this.nodes = nodes; 
            this.listener = l; 
        }
        
        @Override 
        public int getItemViewType(int position) { 
            return nodes.get(position) instanceof String ? TYPE_SEASON_HEADER : TYPE_EPISODE; 
        }
        
        @Override 
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SEASON_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setTextSize(16f); 
                tv.setTextColor(parent.getResources().getColor(R.color.text_primary)); 
                tv.setPadding(8,16,8,8);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                return new RecyclerView.ViewHolder(tv) {};
            }
            View row = inf.inflate(R.layout.item_content_with_actions, parent, false);
            return new EpVH(row);
        }
        
        @Override 
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object node = nodes.get(position);
            if (holder.itemView instanceof TextView) {
                ((TextView) holder.itemView).setText(String.valueOf(node));
            } else if (holder instanceof EpVH) {
                ((EpVH) holder).bind((ContentItem) node, listener);
            }
        }
        
        @Override 
        public int getItemCount() { 
            return nodes.size(); 
        }
        
        static class EpVH extends RecyclerView.ViewHolder {
            android.widget.ImageView poster; 
            TextView title; 
            TextView subtitle; 
            com.google.android.material.button.MaterialButton edit; 
            com.google.android.material.button.MaterialButton delete; 
            
            EpVH(View itemView) { 
                super(itemView); 
                poster = itemView.findViewById(R.id.content_image); 
                title = itemView.findViewById(R.id.content_title); 
                subtitle = itemView.findViewById(R.id.content_meta); 
                edit = itemView.findViewById(R.id.edit_button); 
                delete = itemView.findViewById(R.id.delete_button); 
            }
            
            void bind(ContentItem item, EpisodeActionListener l) {
                title.setText(item.getDisplayTitle());
                subtitle.setText("Episode " + (item.getEpisode() == null ? 1 : item.getEpisode()));
                
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(item.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .centerCrop()
                        .into(poster);
                } else {
                    poster.setImageResource(R.drawable.ic_image_placeholder);
                }
                
                edit.setOnClickListener(v -> l.onOpenEpisodeEditor(item));
                delete.setOnClickListener(v -> l.onDeleteEpisode(item));
            }
        }
    }
}
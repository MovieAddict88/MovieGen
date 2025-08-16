package com.cinecraze.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.utils.DataManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class ManualInputFragment extends Fragment {

    private AutoCompleteTextView contentType;
    private TextInputEditText contentTitle;
    private TextInputEditText contentSubcategory;
    private TextInputEditText contentCountry;
    private TextInputEditText contentDescription;
    private TextInputEditText contentImageUrl;
    private TextInputEditText contentYear;
    private TextInputEditText contentRating;
    private LinearLayout videoSources;
    private TextInputEditText tvSeasons;
    private TextInputEditText tvEpisodesPerSeason;
    private Button addVideoSource;
    private Button generateContent;
    private MaterialCardView tvSeriesSection;

    private DataManager dataManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manual_input, container, false);
        
        initializeViews(view);
        setupListeners();
        initializeServices();
        
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
    }
    
    private void clearDropdownAdapters() {
        try {
            if (contentType != null) contentType.setAdapter(null);
        } catch (Exception e) {
            Log.w("ManualInput", "Error clearing dropdown adapters: " + e.getMessage());
        }
    }

    private void initializeViews(View view) {
        contentType = view.findViewById(R.id.content_type);
        contentTitle = view.findViewById(R.id.content_title);
        contentSubcategory = view.findViewById(R.id.content_subcategory);
        contentCountry = view.findViewById(R.id.content_country);
        contentDescription = view.findViewById(R.id.content_description);
        contentImageUrl = view.findViewById(R.id.content_image_url);
        contentYear = view.findViewById(R.id.content_year);
        contentRating = view.findViewById(R.id.content_rating);
        videoSources = view.findViewById(R.id.video_sources);
        tvSeasons = view.findViewById(R.id.tv_seasons);
        tvEpisodesPerSeason = view.findViewById(R.id.tv_episodes_per_season);
        addVideoSource = view.findViewById(R.id.add_video_source);
        generateContent = view.findViewById(R.id.generate_content);
        tvSeriesSection = view.findViewById(R.id.tv_series_section);
    }

    private void setupListeners() {
        generateContent.setOnClickListener(v -> generateContentFromForm());
        addVideoSource.setOnClickListener(v -> addVideoSourceField());
        
        contentType.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedType = (String) parent.getItemAtPosition(position);
            updateContentTypeUI(selectedType);
        });
    }

    private void setupDropdowns() {
        // Ensure we have a valid context and views before setting up dropdowns
        if (getContext() == null || contentType == null) {
            Log.w("ManualInput", "Cannot setup dropdowns - context or views not available");
            return;
        }
        
        try {
            String[] contentTypes = {"Movie", "TV Series", "Live TV"};
            ArrayAdapter<String> contentTypeAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, contentTypes);
            contentType.setAdapter(contentTypeAdapter);
            
            Log.d("ManualInput", "Dropdowns setup completed successfully");
            
        } catch (Exception e) {
            Log.e("ManualInput", "Error setting up dropdowns: " + e.getMessage(), e);
        }
    }

    private void initializeServices() {
        dataManager = DataManager.getInstance(requireContext());
    }

    private void generateContentFromForm() {
        // Validate required fields
        if (!validateForm()) {
            return;
        }

        generateContent.setEnabled(false);
        generateContent.setText("Generating...");

        try {
            ContentItem content = createContentFromForm();
            
            if (content.getType().equals("TV Series")) {
                generateTVSeriesContent(content);
            } else {
                // Single content item
                dataManager.addContent(content);
                showSuccessMessage("Content generated successfully!");
            }
            
        } catch (Exception e) {
            showErrorMessage("Error generating content: " + e.getMessage());
        } finally {
            generateContent.setEnabled(true);
            generateContent.setText("Generate Content");
        }
    }

    private boolean validateForm() {
        if (contentTitle.getText().toString().trim().isEmpty()) {
            showErrorMessage("Title is required");
            return false;
        }
        
        if (contentType.getText().toString().trim().isEmpty()) {
            showErrorMessage("Content type is required");
            return false;
        }
        
        return true;
    }

    private ContentItem createContentFromForm() {
        ContentItem content = new ContentItem();
        content.setTitle(contentTitle.getText().toString().trim());
        content.setType(contentType.getText().toString().trim());
        content.setSubcategory(contentSubcategory.getText().toString().trim());
        content.setCountry(contentCountry.getText().toString().trim());
        content.setDescription(contentDescription.getText().toString().trim());
        content.setImageUrl(contentImageUrl.getText().toString().trim());
        
        String yearStr = contentYear.getText().toString().trim();
        if (!yearStr.isEmpty()) {
            content.setYear(Integer.parseInt(yearStr));
        }
        
        String ratingStr = contentRating.getText().toString().trim();
        if (!ratingStr.isEmpty()) {
            content.setRating(Double.parseDouble(ratingStr));
        }
        
        // Get video sources
        List<String> sources = getVideoSources();
        content.setServers(sources);
        
        return content;
    }

    private void generateTVSeriesContent(ContentItem baseContent) {
        String seasonsStr = tvSeasons.getText().toString().trim();
        String episodesStr = tvEpisodesPerSeason.getText().toString().trim();
        
        List<ContentItem> seriesItems = new ArrayList<>();
        
        if (seasonsStr.isEmpty()) {
            // Generate all seasons (example: 1-10)
            for (int season = 1; season <= 10; season++) {
                ContentItem seasonItem = createSeasonItem(baseContent, season);
                seriesItems.add(seasonItem);
            }
        } else {
            // Generate specific seasons
            String[] seasonNumbers = seasonsStr.split(",");
            for (String seasonStr : seasonNumbers) {
                int season = Integer.parseInt(seasonStr.trim());
                ContentItem seasonItem = createSeasonItem(baseContent, season);
                seriesItems.add(seasonItem);
            }
        }
        
        dataManager.addContentList(seriesItems);
        showSuccessMessage("TV Series generated successfully! (" + seriesItems.size() + " items)");
    }

    private ContentItem createSeasonItem(ContentItem baseContent, int season) {
        ContentItem seasonItem = new ContentItem();
        seasonItem.setTitle(baseContent.getTitle() + " S" + String.format("%02d", season));
        seasonItem.setType("TV Series");
        seasonItem.setSubcategory(baseContent.getSubcategory());
        seasonItem.setCountry(baseContent.getCountry());
        seasonItem.setDescription(baseContent.getDescription());
        seasonItem.setImageUrl(baseContent.getImageUrl());
        seasonItem.setYear(baseContent.getYear());
        seasonItem.setRating(baseContent.getRating());
        seasonItem.setServers(baseContent.getServers());
        seasonItem.setSeason(season);
        
        // Generate episodes if specified
        String episodesStr = tvEpisodesPerSeason.getText().toString().trim();
        if (!episodesStr.isEmpty()) {
            int episodes = Integer.parseInt(episodesStr);
            List<ContentItem> episodeItems = new ArrayList<>();
            
            for (int episode = 1; episode <= episodes; episode++) {
                ContentItem episodeItem = new ContentItem();
                episodeItem.setTitle(baseContent.getTitle() + " S" + String.format("%02d", season) + 
                                   "E" + String.format("%02d", episode));
                episodeItem.setType("TV Series");
                episodeItem.setSubcategory(baseContent.getSubcategory());
                episodeItem.setCountry(baseContent.getCountry());
                episodeItem.setDescription(baseContent.getDescription());
                episodeItem.setImageUrl(baseContent.getImageUrl());
                episodeItem.setYear(baseContent.getYear());
                episodeItem.setRating(baseContent.getRating());
                episodeItem.setServers(baseContent.getServers());
                episodeItem.setSeason(season);
                episodeItem.setEpisode(episode);
                episodeItems.add(episodeItem);
            }
            
            dataManager.addContentList(episodeItems);
        }
        
        return seasonItem;
    }

    private void addVideoSourceField() {
        // Create a new video source input field
        LinearLayout sourceContainer = new LinearLayout(requireContext());
        sourceContainer.setOrientation(LinearLayout.VERTICAL);
        sourceContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // Add TextInputLayout with TextInputEditText for video source URL
        // This is a simplified version - in a real implementation, you'd create proper TextInputLayout
        
        videoSources.addView(sourceContainer);
    }

    private List<String> getVideoSources() {
        List<String> sources = new ArrayList<>();
        // Implementation to extract video source URLs from the videoSources container
        // This would iterate through all child views and extract the URLs
        return sources;
    }

    private void updateContentTypeUI(String selectedType) {
        if (selectedType.equals("TV Series")) {
            tvSeriesSection.setVisibility(View.VISIBLE);
        } else {
            tvSeriesSection.setVisibility(View.GONE);
        }
    }

    private void showSuccessMessage(String message) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            clearForm();
        });
    }

    private void showErrorMessage(String message) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private void clearForm() {
        contentTitle.setText("");
        contentSubcategory.setText("");
        contentCountry.setText("");
        contentDescription.setText("");
        contentImageUrl.setText("");
        contentYear.setText("");
        contentRating.setText("");
        tvSeasons.setText("");
        tvEpisodesPerSeason.setText("");
        videoSources.removeAllViews();
        tvSeriesSection.setVisibility(View.GONE);
    }
}
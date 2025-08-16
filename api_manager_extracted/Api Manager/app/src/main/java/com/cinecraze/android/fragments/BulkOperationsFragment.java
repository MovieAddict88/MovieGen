package com.cinecraze.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.services.TMDBService;
import com.cinecraze.android.utils.DataManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

public class BulkOperationsFragment extends Fragment {

    // Year-based generation
    private TextInputEditText startYear;
    private TextInputEditText endYear;
    private AutoCompleteTextView yearContentType;
    private AutoCompleteTextView yearGenre;
    private Button generateByYear;

    // Genre-based generation
    private AutoCompleteTextView genreSelect;
    private AutoCompleteTextView genreContentType;
    private TextInputEditText genreMaxResults;
    private Button generateByGenre;

    // Progress section
    private MaterialCardView progressSection;
    private TextView progressStatus;
    private ProgressBar progressBar;
    private TextView progressText;
    private Button cancelGeneration;

    // Results section
    private MaterialCardView resultsSection;
    private TextView resultsSummary;
    private TextView duplicatesFound;

    private TMDBService tmdbService;
    private DataManager dataManager;
    private ExecutorService executor;
    private boolean isGenerating = false;
    private int totalItems = 0;
    private int processedItems = 0;
    private int generatedItems = 0;
    private int skippedItems = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bulk_operations, container, false);
        
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
            if (yearContentType != null) yearContentType.setAdapter(null);
            if (yearGenre != null) yearGenre.setAdapter(null);
            if (genreSelect != null) genreSelect.setAdapter(null);
            if (genreContentType != null) genreContentType.setAdapter(null);
        } catch (Exception e) {
            Log.w("BulkOperations", "Error clearing dropdown adapters: " + e.getMessage());
        }
    }

    private void initializeViews(View view) {
        // Year-based generation
        startYear = view.findViewById(R.id.start_year);
        endYear = view.findViewById(R.id.end_year);
        yearContentType = view.findViewById(R.id.year_content_type);
        yearGenre = view.findViewById(R.id.year_genre);
        generateByYear = view.findViewById(R.id.generate_by_year);

        // Genre-based generation
        genreSelect = view.findViewById(R.id.genre_select);
        genreContentType = view.findViewById(R.id.genre_content_type);
        genreMaxResults = view.findViewById(R.id.genre_max_results);
        generateByGenre = view.findViewById(R.id.generate_by_genre);

        // Progress section
        progressSection = view.findViewById(R.id.progress_section);
        progressStatus = view.findViewById(R.id.progress_status);
        progressBar = view.findViewById(R.id.progress_bar);
        progressText = view.findViewById(R.id.progress_text);
        cancelGeneration = view.findViewById(R.id.cancel_generation);

        // Results section
        resultsSection = view.findViewById(R.id.results_section);
        resultsSummary = view.findViewById(R.id.results_summary);
        duplicatesFound = view.findViewById(R.id.duplicates_found);
    }

    private void setupListeners() {
        generateByYear.setOnClickListener(v -> generateByYear());
        generateByGenre.setOnClickListener(v -> generateByGenre());
        cancelGeneration.setOnClickListener(v -> cancelGeneration());
    }

    private void setupDropdowns() {
        // Ensure we have a valid context and views before setting up dropdowns
        if (getContext() == null || yearContentType == null) {
            Log.w("BulkOperations", "Cannot setup dropdowns - context or views not available");
            return;
        }
        
        try {
            // Content types - create fresh adapters each time
            String[] contentTypes = {"Movie", "TV Series"};
            ArrayAdapter<String> contentTypeAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, contentTypes);
            yearContentType.setAdapter(contentTypeAdapter);
            genreContentType.setAdapter(contentTypeAdapter);

            // Genres - create fresh adapter
            String[] genres = {"All Genres", "Action", "Adventure", "Animation", "Comedy", "Crime", 
                              "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", 
                              "Music", "Mystery", "Romance", "Science Fiction", "TV Movie", 
                              "Thriller", "War", "Western"};
            ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_dropdown_item_1line, genres);
            yearGenre.setAdapter(genreAdapter);
            genreSelect.setAdapter(genreAdapter);
            
            Log.d("BulkOperations", "Dropdowns setup completed successfully");
            
        } catch (Exception e) {
            Log.e("BulkOperations", "Error setting up dropdowns: " + e.getMessage(), e);
        }
    }

    private void initializeServices() {
        tmdbService = new TMDBService(requireContext());
        dataManager = DataManager.getInstance(requireContext());
        executor = Executors.newFixedThreadPool(3);
    }

    private void generateByYear() {
        if (isGenerating) {
            Toast.makeText(requireContext(), "Generation already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        final String startYearStr = startYear.getText().toString().trim();
        final String endYearStr = endYear.getText().toString().trim();
        final String contentType = yearContentType.getText().toString().trim();
        final String genre = yearGenre.getText().toString().trim();

        if (startYearStr.isEmpty() || endYearStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter start and end years", Toast.LENGTH_SHORT).show();
            return;
        }

        final int start = Integer.parseInt(startYearStr);
        final int end = Integer.parseInt(endYearStr);

        if (start > end) {
            Toast.makeText(requireContext(), "Start year must be less than or equal to end year", Toast.LENGTH_SHORT).show();
            return;
        }

        startGeneration("Year-based generation", () -> {
            for (int year = start; year <= end && !Thread.currentThread().isInterrupted(); year++) {
                updateProgress("Processing year " + year + "...");
                
                try {
                    List<ContentItem> yearContent = tmdbService.getContentByYear(year, contentType, genre);
                    processContentList(yearContent);
                    processedItems++;
                    updateProgressUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void generateByGenre() {
        if (isGenerating) {
            Toast.makeText(requireContext(), "Generation already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        final String genre = genreSelect.getText().toString().trim();
        final String contentType = genreContentType.getText().toString().trim();
        final String maxResultsStr = genreMaxResults.getText().toString().trim();

        Log.i("BulkOperations", "Starting genre generation - Genre: " + genre + ", Type: " + contentType + ", Max: " + maxResultsStr);

        if (genre.isEmpty() || contentType.isEmpty()) {
            Toast.makeText(requireContext(), "Please select genre and content type", Toast.LENGTH_SHORT).show();
            return;
        }

        final int maxResults;
        if (!maxResultsStr.isEmpty()) {
            try {
                maxResults = Integer.parseInt(maxResultsStr);
                Log.i("BulkOperations", "Using max results: " + maxResults);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid number format for max results", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            maxResults = 100; // Default
            Log.i("BulkOperations", "Using default max results: " + maxResults);
        }

        startGeneration("Genre-based generation", () -> {
            updateProgress("Processing genre: " + genre + "...");
            
            try {
                Log.i("BulkOperations", "Calling TMDB service for genre content");
                List<ContentItem> genreContent = tmdbService.getContentByGenre(genre, contentType, maxResults);
                
                if (genreContent == null) {
                    Log.e("BulkOperations", "TMDB service returned null content list");
                } else {
                    Log.i("BulkOperations", "TMDB service returned " + genreContent.size() + " items");
                }
                
                processContentList(genreContent);
                processedItems = totalItems;
                updateProgressUI();
                
                Log.i("BulkOperations", "Genre generation completed. Total: " + totalItems + ", Generated: " + generatedItems + ", Skipped: " + skippedItems);
            } catch (Exception e) {
                Log.e("BulkOperations", "Error in genre generation", e);
            }
        });
    }

    private void startGeneration(String type, Runnable generationTask) {
        isGenerating = true;
        totalItems = 0;
        processedItems = 0;
        generatedItems = 0;
        skippedItems = 0;

        // Show progress section
        progressSection.setVisibility(View.VISIBLE);
        resultsSection.setVisibility(View.GONE);
        progressStatus.setText(type);
        progressBar.setProgress(0);
        progressText.setText("0 / 0 items processed");

        // Disable buttons
        generateByYear.setEnabled(false);
        generateByGenre.setEnabled(false);

        executor.submit(() -> {
            try {
                generationTask.run();
            } finally {
                requireActivity().runOnUiThread(() -> {
                    isGenerating = false;
                    generateByYear.setEnabled(true);
                    generateByGenre.setEnabled(true);
                    progressSection.setVisibility(View.GONE);
                    showResults();
                });
            }
        });
    }

    private void processContentList(List<ContentItem> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            Log.w("BulkOperations", "Empty content list received");
            return;
        }

        Log.i("BulkOperations", "Processing " + contentList.size() + " content items");
        totalItems += contentList.size();
        updateProgressUI();

        for (ContentItem content : contentList) {
            if (Thread.currentThread().isInterrupted()) {
                Log.i("BulkOperations", "Thread interrupted, stopping processing");
                break;
            }

            try {
                // Check for duplicates
                if (dataManager.isDuplicate(content)) {
                    skippedItems++;
                    Log.d("BulkOperations", "Skipped duplicate: " + content.getTitle());
                } else {
                    dataManager.addContent(content);
                    generatedItems++;
                    Log.d("BulkOperations", "Added new content: " + content.getTitle());
                }
            } catch (Exception e) {
                Log.e("BulkOperations", "Error processing content: " + content.getTitle(), e);
            }
        }
        
        Log.i("BulkOperations", "Processed content list. Generated: " + generatedItems + ", Skipped: " + skippedItems);
    }

    private void updateProgress(String status) {
        requireActivity().runOnUiThread(() -> {
            progressStatus.setText(status);
        });
    }

    private void updateProgressUI() {
        requireActivity().runOnUiThread(() -> {
            if (totalItems > 0) {
                int progress = (processedItems * 100) / totalItems;
                progressBar.setProgress(progress);
                progressText.setText(processedItems + " / " + totalItems + " items processed");
            }
        });
    }

    private void cancelGeneration() {
        if (isGenerating) {
            executor.shutdownNow();
            isGenerating = false;
            generateByYear.setEnabled(true);
            generateByGenre.setEnabled(true);
            progressSection.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Generation cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResults() {
        resultsSection.setVisibility(View.VISIBLE);
        resultsSummary.setText("Generated " + generatedItems + " items successfully");
        duplicatesFound.setText("Skipped " + skippedItems + " duplicates");
        
        String message = "Generation complete! Generated: " + generatedItems + ", Skipped: " + skippedItems;
        if (generatedItems > 0) {
            message += "\n\nNote: You can view the new content in the Data Management tab or export it to see the full playlist.";
        }
        
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        
        // Notify parent activity about the data change
        if (getActivity() != null) {
            // Send broadcast to refresh other fragments
            getActivity().sendBroadcast(new android.content.Intent("com.cinecraze.CONTENT_UPDATED"));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        clearDropdownAdapters();
    }
}
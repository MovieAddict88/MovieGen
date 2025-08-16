package com.cinecraze.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.services.GitHubService;
import com.cinecraze.android.utils.DataManager;
import com.cinecraze.android.utils.SQLiteExporterTest;
import com.google.android.material.textfield.TextInputEditText;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class DataManagementFragment extends Fragment {

    // Import/Export
    private Button importData;
    private Button exportData;
    private TextView dataStats;

    // GitHub Integration
    private TextInputEditText githubToken;
    private TextInputEditText githubRepo;
    private TextInputEditText githubFilePath;
    private Button uploadToGitHub;
    private Button checkGitHubToken;
    private TextView githubStatus;

    // Data Management
    private Button clearData;
    private Button removeDuplicates;
    private Button migrateStorage;
    private Button optimizeStorage;
    private Button backupData;
    private Button restoreData;
    private Button testDrmHandling;
    private Button testTVSeriesStructure;

    // Storage Info
    private TextView storageInfo;

    private DataManager dataManager;
    private GitHubService githubService;
    private ExecutorService executor;

    private ActivityResultLauncher<Intent> importLauncher;
    
    // Broadcast receiver for content updates
    private BroadcastReceiver contentUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.cinecraze.CONTENT_UPDATED".equals(intent.getAction())) {
                // Refresh stats when content is updated
                updateStats();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data_management, container, false);
        
        initializeViews(view);
        setupListeners();
        initializeServices();
        setupActivityResultLauncher();
        updateStats();
        
        return view;
    }

    private void initializeViews(View view) {
        // Import/Export
        importData = view.findViewById(R.id.import_data);
        exportData = view.findViewById(R.id.export_data);
        dataStats = view.findViewById(R.id.data_stats);

        // GitHub Integration
        githubToken = view.findViewById(R.id.github_token);
        githubRepo = view.findViewById(R.id.github_repo);
        githubFilePath = view.findViewById(R.id.github_file_path);
        uploadToGitHub = view.findViewById(R.id.upload_to_github);
        checkGitHubToken = view.findViewById(R.id.check_github_token);
        githubStatus = view.findViewById(R.id.github_status);

        // Data Management
        clearData = view.findViewById(R.id.clear_data);
        removeDuplicates = view.findViewById(R.id.remove_duplicates);
        migrateStorage = view.findViewById(R.id.migrate_storage);
        optimizeStorage = view.findViewById(R.id.optimize_storage);
        backupData = view.findViewById(R.id.backup_data);
        restoreData = view.findViewById(R.id.restore_data);
        testDrmHandling = view.findViewById(R.id.test_drm_handling);
        testTVSeriesStructure = view.findViewById(R.id.test_tv_series_structure);

        // Storage Info
        storageInfo = view.findViewById(R.id.storage_info);
    }

    private void setupListeners() {
        importData.setOnClickListener(v -> importDataFromFile());
        exportData.setOnClickListener(v -> exportDataToFile());
        uploadToGitHub.setOnClickListener(v -> uploadToGitHub());
        checkGitHubToken.setOnClickListener(v -> checkGitHubToken());
        clearData.setOnClickListener(v -> clearAllData());
        removeDuplicates.setOnClickListener(v -> removeDuplicateItems());
        migrateStorage.setOnClickListener(v -> migrateStorage());
        optimizeStorage.setOnClickListener(v -> optimizeStorage());
        backupData.setOnClickListener(v -> backupData());
        restoreData.setOnClickListener(v -> restoreData());
        testDrmHandling.setOnClickListener(v -> runComprehensiveTests());
        testTVSeriesStructure.setOnClickListener(v -> testTVSeriesImportStructure());
    }

    private void initializeServices() {
        dataManager = DataManager.getInstance(requireContext());
        githubService = new GitHubService();
        executor = Executors.newFixedThreadPool(3);
        
        // Load saved GitHub configuration
        loadGitHubConfig();
    }

    private void setupActivityResultLauncher() {
        importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importDataFromUri(uri);
                    }
                }
            }
        );
    }

    private void loadGitHubConfig() {
        String savedToken = dataManager.getGitHubToken();
        String savedRepo = dataManager.getGitHubRepo();
        String savedPath = dataManager.getGitHubFilePath();
        
        if (savedToken != null) {
            githubToken.setText(savedToken);
        }
        if (savedRepo != null) {
            githubRepo.setText(savedRepo);
        } else {
            githubRepo.setText("MovieAddict88/Movie-Source");
        }
        if (savedPath != null) {
            githubFilePath.setText(savedPath);
        } else {
            githubFilePath.setText("playlist.db");
        }
    }

    private void importDataFromFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        importLauncher.launch(intent);
    }

    private void importDataFromUri(Uri uri) {
        executor.submit(() -> {
            try {
                InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    reader.close();
                    inputStream.close();
                    
                    String jsonData = jsonBuilder.toString();
                    boolean success = dataManager.importFromJson(jsonData);
                    
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            updateStats();
                            showStatus("Data imported successfully!");
                        } else {
                            showStatus("Failed to import data. Invalid JSON format.");
                        }
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Import failed: " + e.getMessage());
                });
            }
        });
    }

    private void exportDataToFile() {
        // Show export options dialog
        String[] options = {"Export as JSON", "Export as SQLite Database", "Export Both"};
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Choose Export Format")
               .setItems(options, (dialog, which) -> {
                   switch (which) {
                       case 0:
                           exportAsJSON();
                           break;
                       case 1:
                           exportAsSQLite();
                           break;
                       case 2:
                           exportBoth();
                           break;
                   }
               })
               .show();
    }
    
    private void exportAsJSON() {
        executor.submit(() -> {
            try {
                String jsonData = dataManager.exportToJson();
                String filename = "cinecraze_playlist_" + System.currentTimeMillis() + ".json";
                
                // Save to Downloads folder
                boolean success = dataManager.saveToDownloads(filename, jsonData);
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("JSON exported to Downloads: " + filename);
                    } else {
                        showStatus("JSON export failed");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("JSON export failed: " + e.getMessage());
                });
            }
        });
    }
    
    private void exportAsSQLite() {
        executor.submit(() -> {
            try {
                Log.i("DataManagement", "Starting SQLite export...");
                
                // First try to generate SQLite data to test if it works
                byte[] dbData = dataManager.generateSQLiteData();
                if (dbData == null) {
                    Log.e("DataManagement", "SQLite data generation returned null");
                    requireActivity().runOnUiThread(() -> {
                        showStatus("❌ SQLite data generation failed - returned null");
                    });
                    return;
                }
                
                if (dbData.length == 0) {
                    Log.e("DataManagement", "SQLite data generation returned empty array");
                    requireActivity().runOnUiThread(() -> {
                        showStatus("❌ SQLite data generation failed - no data to export");
                    });
                    return;
                }
                
                Log.i("DataManagement", "SQLite data generated successfully, size: " + dbData.length + " bytes");
                
                // Export to SQLite database
                boolean success = dataManager.saveSQLiteToDownloads();
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("✅ SQLite database exported to Downloads: playlist.db (" + dbData.length + " bytes)");
                    } else {
                        showStatus("❌ SQLite export failed - check logs for details");
                    }
                });
            } catch (Exception e) {
                Log.e("DataManagement", "SQLite export exception", e);
                requireActivity().runOnUiThread(() -> {
                    showStatus("❌ SQLite export failed: " + e.getMessage());
                });
            }
        });
    }
    
    private void exportBoth() {
        executor.submit(() -> {
            try {
                // Export JSON
                String jsonData = dataManager.exportToJson();
                String jsonFilename = "cinecraze_playlist_" + System.currentTimeMillis() + ".json";
                boolean jsonSuccess = dataManager.saveToDownloads(jsonFilename, jsonData);
                
                // Export SQLite
                boolean sqliteSuccess = dataManager.saveSQLiteToDownloads();
                
                requireActivity().runOnUiThread(() -> {
                    if (jsonSuccess && sqliteSuccess) {
                        showStatus("Both formats exported: " + jsonFilename + " and playlist.db");
                    } else if (jsonSuccess) {
                        showStatus("JSON exported: " + jsonFilename + " (SQLite failed)");
                    } else if (sqliteSuccess) {
                        showStatus("SQLite exported: playlist.db (JSON failed)");
                    } else {
                        showStatus("Both exports failed");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Export failed: " + e.getMessage());
                });
            }
        });
    }

    private void uploadToGitHub() {
        String token = githubToken.getText().toString().trim();
        String repo = githubRepo.getText().toString().trim();
        String filePath = githubFilePath.getText().toString().trim();
        
        if (token.isEmpty()) {
            showStatus("Please enter GitHub token");
            return;
        }
        
        if (repo.isEmpty()) {
            showStatus("Please enter GitHub repository");
            return;
        }
        
        // Show upload format options
        String[] options = {"Upload as JSON", "Upload as SQLite Database", "Upload Both"};
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Choose Upload Format")
               .setItems(options, (dialog, which) -> {
                   switch (which) {
                       case 0:
                           uploadToGitHubAsJSON(token, repo, filePath);
                           break;
                       case 1:
                           uploadToGitHubAsSQLite(token, repo, filePath);
                           break;
                       case 2:
                           uploadToGitHubBoth(token, repo, filePath);
                           break;
                   }
               })
               .show();
    }
    
    private void uploadToGitHubAsJSON(String token, String repo, String filePath) {
        // Save configuration
        dataManager.saveGitHubConfig(token, repo, filePath);
        
        uploadToGitHub.setEnabled(false);
        uploadToGitHub.setText("Uploading JSON...");
        githubStatus.setText("Preparing JSON upload...");
        githubStatus.setVisibility(View.VISIBLE);
        
        executor.submit(() -> {
            try {
                String jsonData = dataManager.exportToJson();
                boolean success = githubService.uploadToGitHub(token, repo, filePath, jsonData);
                final String error = githubService.getLastErrorMessage();
                
                requireActivity().runOnUiThread(() -> {
                    uploadToGitHub.setEnabled(true);
                    uploadToGitHub.setText("Upload to GitHub");
                    
                    if (success) {
                        githubStatus.setText("✅ JSON upload successful!");
                        showStatus("JSON data uploaded to GitHub successfully!");
                    } else {
                        String msg = error == null || error.isEmpty() ? "Upload failed" : error;
                        githubStatus.setText("❌ " + msg);
                        showStatus(msg);
                    }
                    
                    // Hide status after 8 seconds to give time to read
                    githubStatus.postDelayed(() -> githubStatus.setVisibility(View.GONE), 8000);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    uploadToGitHub.setEnabled(true);
                    uploadToGitHub.setText("Upload to GitHub");
                    githubStatus.setText("❌ Upload error: " + e.getMessage());
                    showStatus("Upload failed: " + e.getMessage());
                });
            }
        });
    }
    
    private void uploadToGitHubAsSQLite(String token, String repo, String filePath) {
        // Change file path to .db extension
        final String dbFilePath = filePath.replaceAll("\\.json$", ".db").endsWith(".db") ? 
                                 filePath.replaceAll("\\.json$", ".db") : "playlist.db";
        
        // Save configuration
        dataManager.saveGitHubConfig(token, repo, dbFilePath);
        
        uploadToGitHub.setEnabled(false);
        uploadToGitHub.setText("Uploading SQLite...");
        githubStatus.setText("Preparing SQLite upload...");
        githubStatus.setVisibility(View.VISIBLE);
        
        executor.submit(() -> {
            try {
                // Generate SQLite data directly (no file creation)
                byte[] dbData = dataManager.generateSQLiteData();
                if (dbData != null && dbData.length > 0) {
                    boolean success = githubService.uploadSQLiteToGitHub(token, repo, dbFilePath, dbData);
                    final String error = githubService.getLastErrorMessage();
                    
                    requireActivity().runOnUiThread(() -> {
                        uploadToGitHub.setEnabled(true);
                        uploadToGitHub.setText("Upload to GitHub");
                        
                        if (success) {
                            githubStatus.setText("✅ SQLite upload successful!");
                            showStatus("SQLite database uploaded to GitHub successfully!");
                        } else {
                            String msg = error == null || error.isEmpty() ? "Upload failed" : error;
                            githubStatus.setText("❌ " + msg);
                            showStatus(msg);
                        }
                        
                        // Hide status after 8 seconds to give time to read
                        githubStatus.postDelayed(() -> githubStatus.setVisibility(View.GONE), 8000);
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        uploadToGitHub.setEnabled(true);
                        uploadToGitHub.setText("Upload to GitHub");
                        githubStatus.setText("❌ SQLite data generation failed");
                        showStatus("SQLite data generation failed - check logs for details");
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    uploadToGitHub.setEnabled(true);
                    uploadToGitHub.setText("Upload to GitHub");
                    githubStatus.setText("❌ Upload error: " + e.getMessage());
                    showStatus("Upload failed: " + e.getMessage());
                });
            }
        });
    }
    
    private void uploadToGitHubBoth(String token, String repo, String filePath) {
        // Save configuration
        dataManager.saveGitHubConfig(token, repo, filePath);
        
        uploadToGitHub.setEnabled(false);
        uploadToGitHub.setText("Uploading Both...");
        githubStatus.setText("Preparing uploads...");
        githubStatus.setVisibility(View.VISIBLE);
        
        executor.submit(() -> {
            try {
                // Upload JSON
                String jsonData = dataManager.exportToJson();
                final boolean jsonSuccess = githubService.uploadToGitHub(token, repo, filePath, jsonData);
                
                // Upload SQLite
                final String dbFilePath = filePath.replaceAll("\\.json$", ".db").endsWith(".db") ? 
                                        filePath.replaceAll("\\.json$", ".db") : "playlist.db";
                
                byte[] dbData = dataManager.generateSQLiteData();
                final boolean sqliteSuccess = dbData != null && dbData.length > 0 && 
                                            githubService.uploadSQLiteToGitHub(token, repo, dbFilePath, dbData);
                
                final String error = githubService.getLastErrorMessage();
                
                requireActivity().runOnUiThread(() -> {
                    uploadToGitHub.setEnabled(true);
                    uploadToGitHub.setText("Upload to GitHub");
                    
                    if (jsonSuccess && sqliteSuccess) {
                        githubStatus.setText("✅ Both uploads successful!");
                        showStatus("Both JSON and SQLite uploaded to GitHub successfully!");
                    } else if (jsonSuccess) {
                        githubStatus.setText("✅ JSON uploaded (SQLite failed)");
                        showStatus("JSON uploaded successfully, SQLite upload failed");
                    } else if (sqliteSuccess) {
                        githubStatus.setText("✅ SQLite uploaded (JSON failed)");
                        showStatus("SQLite uploaded successfully, JSON upload failed");
                    } else {
                        String msg = error == null || error.isEmpty() ? "Both uploads failed" : error;
                        githubStatus.setText("❌ " + msg);
                        showStatus(msg);
                    }
                    
                    // Hide status after 8 seconds to give time to read
                    githubStatus.postDelayed(() -> githubStatus.setVisibility(View.GONE), 8000);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    uploadToGitHub.setEnabled(true);
                    uploadToGitHub.setText("Upload to GitHub");
                    githubStatus.setText("❌ Upload error: " + e.getMessage());
                    showStatus("Upload failed: " + e.getMessage());
                });
            }
        });
    }

    private void checkGitHubToken() {
        String token = githubToken.getText().toString().trim();
        if (token.isEmpty()) {
            showStatus("Please enter GitHub token");
            return;
        }
        
        checkGitHubToken.setEnabled(false);
        checkGitHubToken.setText("Checking...");
        
        executor.submit(() -> {
            try {
                boolean isValid = githubService.validateToken(token);
                
                requireActivity().runOnUiThread(() -> {
                    checkGitHubToken.setEnabled(true);
                    checkGitHubToken.setText("Check Token");
                    
                    if (isValid) {
                        showStatus("✅ GitHub token is valid!");
                    } else {
                        showStatus("❌ GitHub token is invalid");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    checkGitHubToken.setEnabled(true);
                    checkGitHubToken.setText("Check Token");
                    showStatus("Token check failed: " + e.getMessage());
                });
            }
        });
    }

    private void testDrmHandling() {
        executor.submit(() -> {
            try {
                boolean success = dataManager.testDrmHandling();
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("✅ DRM handling test passed! DRM and license fields are properly preserved.");
                    } else {
                        showStatus("❌ DRM handling test failed. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("DRM test error: " + e.getMessage());
                });
            }
        });
    }
    
    // Enhanced test method that includes SQLite export testing
    private void runComprehensiveTests() {
        executor.submit(() -> {
            try {
                // Test DRM handling
                boolean drmSuccess = dataManager.testDrmHandling();
                
                // Test TV Series structure
                boolean tvSuccess = dataManager.testTVSeriesImportStructure();
                
                // Test SQLite export
                boolean sqliteSuccess = SQLiteExporterTest.testSQLiteExport(requireContext());
                
                requireActivity().runOnUiThread(() -> {
                    StringBuilder result = new StringBuilder();
                    result.append("Comprehensive Test Results:\n");
                    result.append("DRM Handling: ").append(drmSuccess ? "✅" : "❌").append("\n");
                    result.append("TV Series Structure: ").append(tvSuccess ? "✅" : "❌").append("\n");
                    result.append("SQLite Export: ").append(sqliteSuccess ? "✅" : "❌");
                    
                    showStatus(result.toString());
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Comprehensive test error: " + e.getMessage());
                });
            }
        });
    }

    private void testTVSeriesImportStructure() {
        executor.submit(() -> {
            try {
                boolean success = dataManager.testTVSeriesImportStructure();
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("✅ TV Series import structure test passed! Series are properly structured with seasons and episodes.");
                        updateStats(); // Refresh stats to show the test data
                    } else {
                        showStatus("❌ TV Series import structure test failed. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("TV Series structure test error: " + e.getMessage());
                });
            }
        });
    }
    
    // SQLite export test method - can be called from menu or other UI elements
    private void testSQLiteExport() {
        executor.submit(() -> {
            try {
                boolean success = SQLiteExporterTest.testSQLiteExport(requireContext());
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("✅ SQLite export test passed! Database file created successfully.");
                    } else {
                        showStatus("❌ SQLite export test failed. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("SQLite export test error: " + e.getMessage());
                });
            }
        });
    }

    private void clearAllData() {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("Are you sure you want to clear all data? This action cannot be undone.")
            .setPositiveButton("Clear", (dialog, which) -> {
                executor.submit(() -> {
                    dataManager.clearAllData();
                    requireActivity().runOnUiThread(() -> {
                        updateStats();
                        showStatus("All data cleared");
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void removeDuplicateItems() {
        executor.submit(() -> {
            int removedCount = dataManager.removeDuplicates();
            requireActivity().runOnUiThread(() -> {
                updateStats();
                showStatus("Removed " + removedCount + " duplicate items");
            });
        });
    }

    private void migrateStorage() {
        executor.submit(() -> {
            try {
                boolean success = dataManager.migrateStorage();
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        updateStats();
                        showStatus("Storage migration completed");
                    } else {
                        showStatus("Storage migration failed");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Migration failed: " + e.getMessage());
                });
            }
        });
    }

    private void optimizeStorage() {
        executor.submit(() -> {
            try {
                long savedSpace = dataManager.optimizeStorage();
                requireActivity().runOnUiThread(() -> {
                    updateStats();
                    showStatus("Storage optimized. Saved " + (savedSpace / 1024) + " KB");
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Optimization failed: " + e.getMessage());
                });
            }
        });
    }

    private void backupData() {
        executor.submit(() -> {
            try {
                String backupName = "backup_" + System.currentTimeMillis() + ".json";
                String jsonData = dataManager.exportToJson();
                boolean success = dataManager.saveToFile(backupName, jsonData);
                
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        showStatus("Backup created: " + backupName);
                    } else {
                        showStatus("Backup failed");
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Backup failed: " + e.getMessage());
                });
            }
        });
    }

    private void restoreData() {
        // Show file picker for backup restoration
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        ActivityResultLauncher<Intent> restoreLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        restoreFromBackup(uri);
                    }
                }
            }
        );
        
        restoreLauncher.launch(intent);
    }

    private void restoreFromBackup(Uri uri) {
        executor.submit(() -> {
            try {
                InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    reader.close();
                    inputStream.close();
                    
                    String jsonData = jsonBuilder.toString();
                    boolean success = dataManager.importFromJson(jsonData);
                    
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            updateStats();
                            showStatus("Backup restored successfully!");
                        } else {
                            showStatus("Backup restoration failed. Invalid backup file.");
                        }
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showStatus("Restore failed: " + e.getMessage());
                });
            }
        });
    }

    private void updateStats() {
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
        
        String stats = String.format(
            "📊 Data Statistics\n\n" +
            "Total Items: %d\n" +
            "Movies: %d\n" +
            "TV Series: %d\n" +
            "Live TV: %d\n" +
            "With Servers: %d\n" +
            "Missing Servers: %d",
            totalItems, movies, series, liveTv, itemsWithServers, (totalItems - itemsWithServers)
        );
        
        dataStats.setText(stats);
        
        // Update storage info
        updateStorageInfo();
    }

    private void updateStorageInfo() {
        executor.submit(() -> {
            try {
                long storageSize = dataManager.getStorageSize();
                long availableSpace = dataManager.getAvailableSpace();
                
                String storageInfoText = String.format(
                    "💾 Storage Information\n\n" +
                    "Data Size: %.2f MB\n" +
                    "Available Space: %.2f MB\n" +
                    "Storage Location: Internal Storage",
                    storageSize / (1024.0 * 1024.0),
                    availableSpace / (1024.0 * 1024.0)
                );
                
                requireActivity().runOnUiThread(() -> {
                    storageInfo.setText(storageInfoText);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    storageInfo.setText("Storage information unavailable");
                });
            }
        });
    }

    private void showStatus(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Register broadcast receiver for content updates
        if (getActivity() != null) {
            IntentFilter filter = new IntentFilter("com.cinecraze.CONTENT_UPDATED");
            getActivity().registerReceiver(contentUpdateReceiver, filter);
        }
        // Refresh stats when fragment becomes visible
        updateStats();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        if (getActivity() != null) {
            try {
                getActivity().unregisterReceiver(contentUpdateReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver was not registered, ignore
            }
        }
    }
}
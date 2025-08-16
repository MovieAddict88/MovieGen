package com.cinecraze.android.services;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class GitHubService {
    
    private static final String TAG = "GitHubService";
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String USER_AGENT = "CineCraze-Android/1.0";
    
    private final OkHttpClient client;
    private final Gson gson;
    private String lastErrorMessage = "";
    
    public GitHubService() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }
    
    public String getLastErrorMessage() {
        return lastErrorMessage == null ? "" : lastErrorMessage;
    }

    /**
     * Upload JSON data to GitHub repository
     */
    public boolean uploadToGitHub(String token, String repo, String filePath, String jsonData) {
        lastErrorMessage = "";
        try {
            // Parse repository owner and name
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                lastErrorMessage = "Invalid repository format. Use owner/repo";
                Log.e(TAG, lastErrorMessage + ": " + repo);
                return false;
            }
            
            String owner = repoParts[0];
            String repoName = repoParts[1];
            
            // Determine default branch dynamically
            String defaultBranch = getDefaultBranch(token, owner, repoName);
            if (defaultBranch == null || defaultBranch.isEmpty()) {
                defaultBranch = "main"; // fallback
            }
            
            // Check if file exists to get SHA
            String sha = getFileSha(token, owner, repoName, filePath);
            
            // Prepare upload data
            JsonObject uploadData = new JsonObject();
            uploadData.addProperty("message", "Update " + filePath + " - " + System.currentTimeMillis());
            uploadData.addProperty("content", Base64.getEncoder().encodeToString(jsonData.getBytes()));
            uploadData.addProperty("branch", defaultBranch);
            
            if (sha != null) {
                uploadData.addProperty("sha", sha);
            }
            
            // Upload file
            String uploadUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName + "/contents/" + filePath;
            Request request = new Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", USER_AGENT)
                .put(RequestBody.create(
                    MediaType.parse("application/json"), 
                    gson.toJson(uploadData)
                ))
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Successfully uploaded to GitHub");
                    return true;
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    lastErrorMessage = "GitHub upload failed: " + response.code() + " - " + errorBody;
                    Log.e(TAG, lastErrorMessage);
                    return false;
                }
            }
            
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            Log.e(TAG, "Error uploading to GitHub", e);
            return false;
        }
    }
    
    /**
     * Upload SQLite database data directly to GitHub repository
     */
    public boolean uploadSQLiteToGitHub(String token, String repo, String filePath, byte[] dbData) {
        lastErrorMessage = "";
        try {
            // Parse repository owner and name
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                lastErrorMessage = "Invalid repository format. Use owner/repo";
                Log.e(TAG, lastErrorMessage + ": " + repo);
                return false;
            }
            
            String owner = repoParts[0];
            String repoName = repoParts[1];
            
            // Determine default branch dynamically
            String defaultBranch = getDefaultBranch(token, owner, repoName);
            if (defaultBranch == null || defaultBranch.isEmpty()) {
                defaultBranch = "main"; // fallback
            }
            
            // Check if file exists to get SHA
            String sha = getFileSha(token, owner, repoName, filePath);
            
            // Encode database data to base64
            String base64Content = Base64.getEncoder().encodeToString(dbData);
            
            // Prepare upload data
            JsonObject uploadData = new JsonObject();
            uploadData.addProperty("message", "Update " + filePath + " - " + System.currentTimeMillis());
            uploadData.addProperty("content", base64Content);
            uploadData.addProperty("branch", defaultBranch);
            
            if (sha != null) {
                uploadData.addProperty("sha", sha);
            }
            
            // Upload file
            String uploadUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName + "/contents/" + filePath;
            Request request = new Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", USER_AGENT)
                .put(RequestBody.create(
                    MediaType.parse("application/json"), 
                    gson.toJson(uploadData)
                ))
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Successfully uploaded SQLite database to GitHub");
                    return true;
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    lastErrorMessage = "GitHub upload failed: " + response.code() + " - " + errorBody;
                    Log.e(TAG, lastErrorMessage);
                    return false;
                }
            }
            
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            Log.e(TAG, "Error uploading SQLite database to GitHub", e);
            return false;
        }
    }
    
    /**
     * Upload SQLite database file to GitHub repository (legacy method)
     */
    public boolean uploadSQLiteFileToGitHub(String token, String repo, String filePath, java.io.File dbFile) {
        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(dbFile.toPath());
            return uploadSQLiteToGitHub(token, repo, filePath, fileBytes);
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            Log.e(TAG, "Error reading SQLite file for upload", e);
            return false;
        }
    }
    
    /**
     * Validate GitHub token
     */
    public boolean validateToken(String token) {
        try {
            Request request = new Request.Builder()
                .url(GITHUB_API_BASE + "/user")
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating GitHub token", e);
            return false;
        }
    }
    
    /**
     * Get file SHA if it exists
     */
    private String getFileSha(String token, String owner, String repo, String filePath) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + filePath;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject fileInfo = JsonParser.parseString(json).getAsJsonObject();
                    return fileInfo.get("sha").getAsString();
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting file SHA (file may not exist)", e);
        }
        
        return null;
    }
    
    /**
     * Download file from GitHub
     */
    public String downloadFromGitHub(String token, String repo, String filePath) {
        try {
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                Log.e(TAG, "Invalid repository format: " + repo);
                return null;
            }
            
            String owner = repoParts[0];
            String repoName = repoParts[1];
            
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName + "/contents/" + filePath;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject fileInfo = JsonParser.parseString(json).getAsJsonObject();
                    String content = fileInfo.get("content").getAsString();
                    String encoding = fileInfo.get("encoding").getAsString();
                    
                    if ("base64".equals(encoding)) {
                        byte[] decodedBytes = Base64.getDecoder().decode(content);
                        return new String(decodedBytes);
                    } else {
                        Log.e(TAG, "Unsupported encoding: " + encoding);
                        return null;
                    }
                } else {
                    Log.e(TAG, "Failed to download file: " + response.code());
                    return null;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error downloading from GitHub", e);
            return null;
        }
    }
    
    /**
     * Check repository access
     */
    public boolean checkRepositoryAccess(String token, String repo) {
        try {
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                return false;
            }
            
            String owner = repoParts[0];
            String repoName = repoParts[1];
            
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking repository access", e);
            return false;
        }
    }
    
    /**
     * Get repository information
     */
    public JsonObject getRepositoryInfo(String token, String repo) {
        try {
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                return null;
            }
            
            String owner = repoParts[0];
            String repoName = repoParts[1];
            
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    return JsonParser.parseString(json).getAsJsonObject();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting repository info", e);
        }
        
        return null;
    }
    
    /**
     * Create a new repository (if user has permissions)
     */
    public boolean createRepository(String token, String repoName, String description) {
        try {
            String[] repoParts = repoName.split("/");
            if (repoParts.length != 2) {
                Log.e(TAG, "Invalid repository format: " + repoName);
                return false;
            }
            
            String owner = repoParts[0];
            String name = repoParts[1];
            
            // Only allow creating repos under the authenticated user
            if (!owner.equals(getAuthenticatedUser(token))) {
                Log.e(TAG, "Can only create repositories under authenticated user");
                return false;
            }
            
            JsonObject repoData = new JsonObject();
            repoData.addProperty("name", name);
            repoData.addProperty("description", description);
            repoData.addProperty("private", false);
            repoData.addProperty("auto_init", true);
            
            String url = GITHUB_API_BASE + "/user/repos";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .post(RequestBody.create(
                    MediaType.parse("application/json"), 
                    gson.toJson(repoData)
                ))
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating repository", e);
            return false;
        }
    }
    
    /**
     * Get authenticated user
     */
    private String getAuthenticatedUser(String token) {
        try {
            Request request = new Request.Builder()
                .url(GITHUB_API_BASE + "/user")
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject userInfo = JsonParser.parseString(json).getAsJsonObject();
                    return userInfo.get("login").getAsString();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting authenticated user", e);
        }
        
        return null;
    }

    private String getDefaultBranch(String token, String owner, String repoName) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repoName;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", USER_AGENT)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject info = JsonParser.parseString(json).getAsJsonObject();
                    if (info.has("default_branch")) {
                        return info.get("default_branch").getAsString();
                    }
                } else {
                    Log.w(TAG, "Failed to get default branch: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting default branch", e);
        }
        return null;
    }
}
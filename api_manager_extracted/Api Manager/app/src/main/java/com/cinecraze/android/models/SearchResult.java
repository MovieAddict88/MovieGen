package com.cinecraze.android.models;

import com.google.gson.annotations.SerializedName;

public class SearchResult {
    
    @SerializedName("id")
    private int id;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("overview")
    private String overview;
    
    @SerializedName("poster_path")
    private String posterPath;
    
    @SerializedName("backdrop_path")
    private String backdropPath;
    
    @SerializedName("media_type")
    private String mediaType;
    
    @SerializedName("release_date")
    private String releaseDate;
    
    @SerializedName("first_air_date")
    private String firstAirDate;
    
    @SerializedName("vote_average")
    private Double voteAverage;
    
    @SerializedName("vote_count")
    private Integer voteCount;
    
    @SerializedName("popularity")
    private Double popularity;

    // Constructors
    public SearchResult() {}

    public SearchResult(int id, String title, String mediaType) {
        this.id = id;
        this.title = title;
        this.mediaType = mediaType;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null ? title : name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getBackdropPath() {
        return backdropPath;
    }

    public void setBackdropPath(String backdropPath) {
        this.backdropPath = backdropPath;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getFirstAirDate() {
        return firstAirDate;
    }

    public void setFirstAirDate(String firstAirDate) {
        this.firstAirDate = firstAirDate;
    }

    public Double getVoteAverage() {
        return voteAverage;
    }

    public void setVoteAverage(Double voteAverage) {
        this.voteAverage = voteAverage;
    }

    public Integer getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Integer voteCount) {
        this.voteCount = voteCount;
    }

    public Double getPopularity() {
        return popularity;
    }

    public void setPopularity(Double popularity) {
        this.popularity = popularity;
    }

    // Helper methods
    public String getDisplayTitle() {
        return getTitle();
    }

    public String getDisplayDate() {
        if (mediaType != null && mediaType.equals("tv")) {
            return firstAirDate;
        }
        return releaseDate;
    }

    public String getFullPosterUrl() {
        if (posterPath != null && !posterPath.isEmpty()) {
            return "https://image.tmdb.org/t/p/w500" + posterPath;
        }
        return null;
    }

    public String getFullBackdropUrl() {
        if (backdropPath != null && !backdropPath.isEmpty()) {
            return "https://image.tmdb.org/t/p/original" + backdropPath;
        }
        return null;
    }

    public String getFormattedRating() {
        if (voteAverage != null) {
            return String.format("%.1f", voteAverage);
        }
        return "N/A";
    }

    public String getFormattedYear() {
        String date = getDisplayDate();
        if (date != null && date.length() >= 4) {
            return date.substring(0, 4);
        }
        return "N/A";
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "id=" + id +
                ", title='" + getTitle() + '\'' +
                ", mediaType='" + mediaType + '\'' +
                ", releaseDate='" + releaseDate + '\'' +
                ", voteAverage=" + voteAverage +
                '}';
    }
}
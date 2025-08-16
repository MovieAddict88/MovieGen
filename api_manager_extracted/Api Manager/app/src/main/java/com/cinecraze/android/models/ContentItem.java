package com.cinecraze.android.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@Entity(tableName = "content_items")
public class ContentItem {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("type")
    private String type; // Movie, TV Series, Live TV
    
    @SerializedName("subcategory")
    private String subcategory;
    
    @SerializedName("country")
    private String country;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("image_url")
    private String imageUrl;
    
    @SerializedName("year")
    private Integer year;
    
    @SerializedName("rating")
    private Double rating;
    
    @SerializedName("duration")
    private String duration;
    
    @SerializedName("servers")
    private List<String> servers;
    
    @SerializedName("season")
    private Integer season;
    
    @SerializedName("episode")
    private Integer episode;
    
    @SerializedName("series_title")
    private String seriesTitle;
    
    @SerializedName("tmdb_id")
    private Integer tmdbId;
    
    @SerializedName("created_at")
    private Long createdAt;
    
    @SerializedName("updated_at")
    private Long updatedAt;

    // Constructors
    public ContentItem() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public ContentItem(String title, String type) {
        this();
        this.title = title;
        this.type = type;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        // Ensure we store a mutable copy
        if (servers == null) {
            this.servers = null;
        } else {
            this.servers = new java.util.ArrayList<>(servers);
        }
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public Integer getEpisode() {
        return episode;
    }

    public void setEpisode(Integer episode) {
        this.episode = episode;
    }

    public String getSeriesTitle() {
        return seriesTitle;
    }

    public void setSeriesTitle(String seriesTitle) {
        this.seriesTitle = seriesTitle;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDisplayTitle() {
        if ("TV Series".equals(type) && seriesTitle != null && !seriesTitle.trim().isEmpty()) {
            if (season != null && episode != null) {
                return seriesTitle + " S" + String.format("%02d", season) + "E" + String.format("%02d", episode);
            } else if (season != null) {
                return seriesTitle + " S" + String.format("%02d", season);
            }
            return seriesTitle;
        } else {
            if (season != null && episode != null) {
                return title + " S" + String.format("%02d", season) + "E" + String.format("%02d", episode);
            } else if (season != null) {
                return title + " S" + String.format("%02d", season);
            }
            return title;
        }
    }

    public boolean hasServers() {
        return servers != null && !servers.isEmpty();
    }

    public void addServer(String server) {
        if (servers == null) {
            servers = new java.util.ArrayList<>();
        }
        servers.add(server);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ContentItem that = (ContentItem) obj;
        
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (tmdbId != null ? !tmdbId.equals(that.tmdbId) : that.tmdbId != null) return false;
        if (season != null ? !season.equals(that.season) : that.season != null) return false;
        if (episode != null ? !episode.equals(that.episode) : that.episode != null) return false;
        if (seriesTitle != null ? !seriesTitle.equals(that.seriesTitle) : that.seriesTitle != null) return false;
        
        return true;
    }

    @Override
    public int hashCode() {
        int result = title != null ? title.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (tmdbId != null ? tmdbId.hashCode() : 0);
        result = 31 * result + (season != null ? season.hashCode() : 0);
        result = 31 * result + (episode != null ? episode.hashCode() : 0);
        result = 31 * result + (seriesTitle != null ? seriesTitle.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ContentItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", tmdbId=" + tmdbId +
                ", season=" + season +
                ", episode=" + episode +
                ", seriesTitle='" + seriesTitle + '\'' +
                '}';
    }
}
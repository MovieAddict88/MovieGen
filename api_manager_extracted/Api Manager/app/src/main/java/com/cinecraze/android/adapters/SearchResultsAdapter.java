package com.cinecraze.android.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.cinecraze.android.R;
import com.cinecraze.android.models.SearchResult;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int ITEMS_PER_PAGE = 20; // Limit items per page to prevent crashes
    private static final int MAX_VISIBLE_ITEMS = 50; // Maximum items to keep in memory
    
    // View types
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING_MORE = 1;
    
    private List<SearchResult> allResults; // All search results
    private List<SearchResult> displayedResults; // Currently displayed results (paginated)
    private OnItemClickListener listener;
    private OnGenerateClickListener generateListener;
    private OnLoadMoreListener loadMoreListener;
    
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMoreItems = true;
    private boolean showLoadMoreButton = true; // Whether to show load more button

    public interface OnItemClickListener {
        void onItemClick(SearchResult result);
    }

    public interface OnGenerateClickListener {
        void onGenerateClick(SearchResult result);
    }
    
    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    public SearchResultsAdapter() {
        this.allResults = new ArrayList<>();
        this.displayedResults = new ArrayList<>();
    }

    public SearchResultsAdapter(List<SearchResult> results, OnItemClickListener listener) {
        this.allResults = results != null ? new ArrayList<>(results) : new ArrayList<>();
        this.displayedResults = new ArrayList<>();
        this.listener = listener;
        
        // Only load first page if we actually have results
        if (this.allResults != null && !this.allResults.isEmpty()) {
            loadPage(0);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnGenerateClickListener(OnGenerateClickListener listener) {
        this.generateListener = listener;
    }
    
    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        this.loadMoreListener = listener;
    }

    public void setSearchResults(List<SearchResult> results) {
        Log.d("SearchAdapter", "setSearchResults called with " + (results != null ? results.size() : 0) + " results");
        
        this.allResults.clear();
        this.displayedResults.clear();
        
        if (results != null) {
            this.allResults.addAll(results);
        }
        
        currentPage = 0;
        hasMoreItems = allResults.size() > ITEMS_PER_PAGE;
        isLoading = false;
        
        Log.d("SearchAdapter", "After setSearchResults - total: " + allResults.size() + 
              ", hasMoreItems: " + hasMoreItems + ", will show: " + Math.min(ITEMS_PER_PAGE, allResults.size()));
        
        loadPage(0);
    }
    
    private void loadPage(int page) {
        if (allResults == null || allResults.isEmpty()) {
            Log.d("SearchAdapter", "No results to load for page " + page);
            isLoading = false;
            return;
        }
        
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allResults.size());
        
        Log.d("SearchAdapter", "Loading page " + page + " - indices " + startIndex + " to " + endIndex + " of " + allResults.size());
        
        if (startIndex < allResults.size()) {
            List<SearchResult> pageResults = allResults.subList(startIndex, endIndex);
            
            if (displayedResults == null) {
                displayedResults = new ArrayList<>();
            }
            
            if (page == 0) {
                // First page - replace all
                displayedResults.clear();
                displayedResults.addAll(pageResults);
                hasMoreItems = endIndex < allResults.size();
                notifyDataSetChanged();
            } else {
                // Additional pages - append
                int oldSize = displayedResults.size();
                boolean hadMoreItemsBefore = hasMoreItems;
                
                displayedResults.addAll(pageResults);
                hasMoreItems = endIndex < allResults.size();
                
                // Notify that items were inserted
                notifyItemRangeInserted(oldSize, pageResults.size());
                
                // If we had a footer before and still have one, update it
                // If we had a footer but no longer have one, remove it
                // If we didn't have a footer but now have one, add it
                if (hadMoreItemsBefore && hasMoreItems) {
                    // Update existing footer
                    notifyItemChanged(getItemCount() - 1);
                } else if (hadMoreItemsBefore && !hasMoreItems) {
                    // Remove footer (no more items)
                    notifyItemRemoved(oldSize + pageResults.size());
                } else if (!hadMoreItemsBefore && hasMoreItems) {
                    // Add footer (now have more items)
                    notifyItemInserted(getItemCount() - 1);
                }
            }
            
            Log.d("SearchAdapter", "Loaded page " + page + ", total displayed: " + displayedResults.size() + 
                  ", hasMore: " + hasMoreItems);
        }
        
        isLoading = false;
    }
    
    public void loadMoreItems() {
        if (!isLoading && hasMoreItems()) {
            Log.d("SearchAdapter", "loadMoreItems called - currentPage: " + currentPage + ", hasMore: " + hasMoreItems);
            isLoading = true;
            
            // Update the footer view to show loading state
            if (hasMoreItems()) {
                notifyItemChanged(getItemCount() - 1);
            }
            
            currentPage++;
            loadPage(currentPage);
        }
    }

    public void addSearchResults(List<SearchResult> results) {
        if (results != null) {
            allResults.addAll(results);
            hasMoreItems = displayedResults.size() < allResults.size();
            
            // If we have room to display more items, load them
            if (displayedResults.size() < MAX_VISIBLE_ITEMS && hasMoreItems) {
                loadMoreItems();
            }
        }
    }

    public void clearResults() {
        this.allResults.clear();
        this.displayedResults.clear();
        this.currentPage = 0;
        this.hasMoreItems = false;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (hasMoreItems() && position == displayedResults.size()) {
            return VIEW_TYPE_LOADING_MORE;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING_MORE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_load_more, parent, false);
            return new LoadingViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingViewHolder) {
            // Bind loading view
            LoadingViewHolder loadingViewHolder = (LoadingViewHolder) holder;
            loadingViewHolder.bind();
        } else {
            ViewHolder viewHolder = (ViewHolder) holder;
            if (displayedResults == null || position < 0 || position >= displayedResults.size()) {
                Log.w("SearchAdapter", "Invalid position " + position + " for adapter with " + 
                      (displayedResults != null ? displayedResults.size() : 0) + " items");
                return;
            }
            
            try {
                SearchResult result = displayedResults.get(position);
                if (result != null) {
                    viewHolder.bind(result);
                }
            } catch (Exception e) {
                Log.e("SearchAdapter", "Error binding view at position " + position, e);
            }
        }
    }

    @Override
    public int getItemCount() {
        int baseCount = displayedResults != null ? displayedResults.size() : 0;
        // Add 1 for load more button if there are more items
        return hasMoreItems() ? baseCount + 1 : baseCount;
    }
    
    public int getTotalItemCount() {
        return allResults != null ? allResults.size() : 0;
    }
    
    public boolean hasMoreItems() {
        boolean hasMore = hasMoreItems && allResults != null && displayedResults != null && 
               displayedResults.size() < allResults.size();
        Log.d("SearchAdapter", "hasMoreItems: " + hasMore + " (flag: " + hasMoreItems + 
              ", displayed: " + (displayedResults != null ? displayedResults.size() : 0) + 
              ", total: " + (allResults != null ? allResults.size() : 0) + ")");
        return hasMore;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView posterImage;
        private TextView titleText;
        private TextView metaText;
        private MaterialButton generateButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.poster_image);
            titleText = itemView.findViewById(R.id.title_text);
            metaText = itemView.findViewById(R.id.meta_text);
            generateButton = itemView.findViewById(R.id.generate_button);

            // Handle generate button click (adds directly to playlist)
            generateButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < displayedResults.size() && generateListener != null) {
                    generateListener.onGenerateClick(displayedResults.get(position));
                }
            });

            // Handle card click (fill form / preview)
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < displayedResults.size() && listener != null) {
                    listener.onItemClick(displayedResults.get(position));
                }
            });
        }

        public void bind(SearchResult result) {
            titleText.setText(result.getDisplayTitle());
            
            // Create metadata string like the HTML version
            String mediaType = result.getMediaType();
            String mediaTypeEmoji = "movie".equals(mediaType) ? "🎬" : "📺";
            String mediaTypeText = "movie".equals(mediaType) ? "MOVIE" : "TV SERIES";
            String metaString = String.format("%s • %s %s • ID: %d", 
                result.getFormattedYear(), 
                mediaTypeEmoji, 
                mediaTypeText, 
                result.getId());
            metaText.setText(metaString);

            // Load poster image with optimized settings to prevent memory issues
            String posterUrl = result.getFullPosterUrl();
            if (posterUrl != null && !posterUrl.isEmpty()) {
                RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_movie)
                    .error(R.drawable.ic_movie)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache resized images
                    .override(200, 300); // Limit image size to prevent memory issues
                
                Glide.with(itemView.getContext())
                    .load(posterUrl)
                    .apply(options)
                    .into(posterImage);
            } else {
                posterImage.setImageResource(R.drawable.ic_movie);
            }
        }
    }

    public class LoadingViewHolder extends RecyclerView.ViewHolder {
        private MaterialButton loadMoreButton;
        private TextView paginationInfo;

        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            loadMoreButton = itemView.findViewById(R.id.load_more_button);
            paginationInfo = itemView.findViewById(R.id.pagination_info);

            loadMoreButton.setOnClickListener(v -> {
                if (hasMoreItems() && !isLoading) {
                    Log.d("SearchAdapter", "Load More button clicked, calling loadMoreItems...");
                    // Call the adapter's loadMoreItems method
                    loadMoreItems();
                }
            });
        }

        public void bind() {
            int displayedCount = displayedResults != null ? displayedResults.size() : 0;
            int totalCount = allResults != null ? allResults.size() : 0;
            
            Log.d("SearchAdapter", "LoadingViewHolder.bind() - displayed: " + displayedCount + 
                  ", total: " + totalCount + ", isLoading: " + isLoading + ", hasMore: " + hasMoreItems());
            
            loadMoreButton.setEnabled(!isLoading && hasMoreItems());
            
            if (isLoading) {
                loadMoreButton.setText("Loading...");
                paginationInfo.setText("Loading more results...");
            } else if (hasMoreItems()) {
                loadMoreButton.setText("Load More Results");
                paginationInfo.setText(String.format("Showing %d of %d results", displayedCount, totalCount));
            } else {
                loadMoreButton.setText("No More Results");
                loadMoreButton.setEnabled(false);
                paginationInfo.setText(String.format("All %d results loaded", totalCount));
            }
        }
        
        public void resetLoadingState() {
            isLoading = false;
            loadMoreButton.setEnabled(hasMoreItems());
            bind(); // Refresh the display
        }
    }
}
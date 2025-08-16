package com.cinecraze.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ContentItem;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class ContentPreviewAdapter extends RecyclerView.Adapter<ContentPreviewAdapter.ViewHolder> {
    
    private List<ContentItem> contentItems;
    private ContentActionListener listener;

    public interface ContentActionListener {
        void onEditContent(ContentItem item);
        void onAddServer(ContentItem item);
        void onDeleteContent(ContentItem item);
    }

    public ContentPreviewAdapter(List<ContentItem> items, ContentActionListener listener) {
        this.contentItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.listener = listener;
    }

    public void updateContent(List<ContentItem> newItems) {
        this.contentItems.clear();
        if (newItems != null) {
            this.contentItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void addContent(ContentItem item) {
        this.contentItems.add(item);
        notifyItemInserted(contentItems.size() - 1);
    }

    public void removeContent(ContentItem item) {
        int position = contentItems.indexOf(item);
        if (position != -1) {
            contentItems.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_content_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContentItem item = contentItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return contentItems.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView contentImage;
        private TextView titleText;
        private TextView metaText;
        private MaterialButton editButton;
        private MaterialButton addServerButton;
        private MaterialButton deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            contentImage = itemView.findViewById(R.id.content_image);
            titleText = itemView.findViewById(R.id.content_title);
            metaText = itemView.findViewById(R.id.content_meta);
            editButton = itemView.findViewById(R.id.edit_button);
            addServerButton = itemView.findViewById(R.id.add_server_button);
            deleteButton = itemView.findViewById(R.id.delete_button);

            // Setup button click listeners
            editButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEditContent(contentItems.get(position));
                }
            });

            addServerButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAddServer(contentItems.get(position));
                }
            });

            deleteButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteContent(contentItems.get(position));
                }
            });
        }

        public void bind(ContentItem item) {
            // Set title
            titleText.setText(item.getTitle());

            // Set meta information
            StringBuilder metaBuilder = new StringBuilder();
            
            // Year/Status
            if (item.getYear() != null) {
                metaBuilder.append(item.getYear());
            } else {
                metaBuilder.append("Unknown");
            }
            
            // Live Status
            if ("Live TV".equals(item.getType())) {
                metaBuilder.append(" • LIVE");
            } else if ("Movie".equals(item.getType())) {
                metaBuilder.append(" • MOVIE");
            } else if ("TV Series".equals(item.getType())) {
                metaBuilder.append(" • SERIES");
            }
            
            // Rating
            metaBuilder.append(" • Rating: ");
            if (item.getRating() != null) {
                metaBuilder.append(String.format("%.1f", item.getRating()));
            } else {
                metaBuilder.append("N/A");
            }
            
            // Category
            metaBuilder.append(" • Category: ");
            if (item.getType() != null) {
                metaBuilder.append(item.getType());
            } else {
                metaBuilder.append("N/A");
            }
            
            // SubCategory
            metaBuilder.append(" • SubCategory: ");
            if (item.getSubcategory() != null && !item.getSubcategory().isEmpty()) {
                metaBuilder.append(item.getSubcategory());
            } else {
                metaBuilder.append("N/A");
            }
            
            // Servers count
            metaBuilder.append(" • Servers: ");
            if (item.getServers() != null && !item.getServers().isEmpty()) {
                metaBuilder.append(item.getServers().size());
            } else {
                metaBuilder.append("0");
            }
            
            metaText.setText(metaBuilder.toString());

            // Load image using Glide
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                Glide.with(contentImage.getContext())
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(contentImage);
            } else {
                // Set default placeholder image
                contentImage.setImageResource(R.drawable.ic_image_placeholder);
            }
        }
    }
}
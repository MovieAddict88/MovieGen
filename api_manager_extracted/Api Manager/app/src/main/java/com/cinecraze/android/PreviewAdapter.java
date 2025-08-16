package com.cinecraze.android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.cinecraze.android.models.ContentItem;
import com.cinecraze.android.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ItemActionsListener {
        void onGenerateMetadata(ContentItem item, Integer tmdbIdOverride);
        void onGenerateServers(ContentItem item);
        void onFillSeasonsEpisodes(ContentItem item);
        // Group actions
        void onFillMissingForSeries(String seriesTitle, Integer tmdbId);
        void onGenerateServersForSeries(String seriesTitle);
        void onUpdateMetadataForSeries(String seriesTitle, Integer tmdbId);
    }

    private static final int TYPE_MOVIE = 0;
    private static final int TYPE_SERIES_GROUP = 1;

    private final List<Object> entries = new ArrayList<>(); // ContentItem for movies, SeriesGroup for TV series
    private ItemActionsListener actionsListener;

    static class SeriesGroup {
        final String seriesTitle;
        final String posterUrl;
        final int seasons;
        final int episodes;
        final Integer tmdbId; // may be null
        SeriesGroup(String t, String p, int s, int e, Integer id){seriesTitle=t;posterUrl=p;seasons=s;episodes=e;tmdbId=id;}
    }

    public void setItems(List<ContentItem> content) {
        entries.clear();
        if (content != null) {
            // Group series by seriesTitle
            Map<String, List<ContentItem>> seriesMap = new HashMap<>();
            List<ContentItem> movies = new ArrayList<>();
            for (ContentItem it : content) {
                if ("TV Series".equals(it.getType())) {
                    String title = it.getSeriesTitle() != null && !it.getSeriesTitle().trim().isEmpty() ? it.getSeriesTitle() : extractSeriesTitle(it.getTitle());
                    seriesMap.computeIfAbsent(title, k -> new ArrayList<>()).add(it);
                } else if ("Movie".equals(it.getType())) {
                    movies.add(it);
                }
            }
            // Add grouped series
            for (Map.Entry<String, List<ContentItem>> e : seriesMap.entrySet()) {
                List<ContentItem> list = e.getValue();
                int seasons = (int) list.stream().map(x -> x.getSeason() == null ? 1 : x.getSeason()).distinct().count();
                int eps = list.size();
                String poster = list.get(0).getImageUrl();
                Integer tId = list.get(0).getTmdbId();
                entries.add(new SeriesGroup(e.getKey(), poster, seasons, eps, tId));
            }
            // Add movies
            entries.addAll(movies);
        }
        notifyDataSetChanged();
    }

    public void setActionsListener(ItemActionsListener listener) {
        this.actionsListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return (entries.get(position) instanceof SeriesGroup) ? TYPE_SERIES_GROUP : TYPE_MOVIE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SERIES_GROUP) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_series_group_preview, parent, false);
            return new SeriesVH(v);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preview_card, parent, false);
        return new MovieVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = entries.get(position);
        if (holder instanceof SeriesVH) {
            ((SeriesVH) holder).bind((SeriesGroup) obj, actionsListener);
        } else {
            ((MovieVH) holder).bind((ContentItem) obj, actionsListener);
        }
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static class MovieVH extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        TextView meta;
        TextView flags;
        MaterialButton btnGenerate;
        View tmdbRow;
        TextInputEditText inputTmdb;
        MaterialButton btnConfirmTmdb;
        MovieVH(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.preview_poster);
            title = itemView.findViewById(R.id.preview_title_text);
            meta = itemView.findViewById(R.id.preview_meta);
            flags = itemView.findViewById(R.id.preview_flags);
            btnGenerate = itemView.findViewById(R.id.btn_generate);
            tmdbRow = itemView.findViewById(R.id.tmdb_input_row);
            inputTmdb = itemView.findViewById(R.id.input_tmdb_id);
            btnConfirmTmdb = itemView.findViewById(R.id.btn_confirm_tmdb);
        }
        void bind(ContentItem item, ItemActionsListener listener) {
            title.setText(item.getDisplayTitle());
            
            // Set meta info
            StringBuilder metaSb = new StringBuilder();
            if (item.getYear() != null) metaSb.append(item.getYear());
            metaSb.append(" • ").append(item.getType() != null ? item.getType().toUpperCase() : "UNKNOWN");
            if (item.getTmdbId() != null) metaSb.append(" • ID: ").append(item.getTmdbId());
            meta.setText(metaSb.toString());
            
            String posterUrl = item.getImageUrl();
            RequestOptions options = new RequestOptions().placeholder(R.drawable.ic_movie).error(R.drawable.ic_movie).centerCrop().override(240, 360);
            if (posterUrl != null && !posterUrl.isEmpty()) Glide.with(itemView.getContext()).load(posterUrl).apply(options).into(poster); else poster.setImageResource(R.drawable.ic_movie);
            
            StringBuilder sb = new StringBuilder();
            boolean needsPoster = posterUrl == null || posterUrl.trim().isEmpty();
            boolean needsDesc = item.getDescription() == null || item.getDescription().trim().isEmpty();
            boolean needsRating = item.getRating() == null;
            boolean needsYear = item.getYear() == null;
            boolean needsGenre = item.getSubcategory() == null || item.getSubcategory().trim().isEmpty();
            boolean needsServers = item.getServers() == null || item.getServers().isEmpty();
            if (needsPoster) sb.append("Poster "); if (needsDesc) sb.append("Desc "); if (needsRating) sb.append("Rating "); if (needsYear) sb.append("Year "); if (needsGenre) sb.append("Genre "); if (needsServers) sb.append("Servers ");
            if (sb.length() == 0) { 
                flags.setText("✓ Complete"); 
                flags.setTextColor(itemView.getResources().getColor(R.color.netflix_text_white)); 
                flags.setBackgroundColor(itemView.getResources().getColor(R.color.success));
            } else { 
                flags.setText("⚠ " + sb.toString().trim()); 
                flags.setTextColor(itemView.getResources().getColor(R.color.netflix_text_white)); 
                flags.setBackgroundColor(itemView.getResources().getColor(R.color.netflix_red));
            }
            tmdbRow.setVisibility(View.GONE);
            btnGenerate.setOnClickListener(v -> { if (listener==null) return; if (item.getTmdbId()!=null) listener.onGenerateMetadata(item,null); else tmdbRow.setVisibility(View.VISIBLE); });
            btnConfirmTmdb.setOnClickListener(v -> { if (listener==null) return; Integer overrideId = readInt(inputTmdb); if (overrideId!=null) { listener.onGenerateMetadata(item, overrideId); tmdbRow.setVisibility(View.GONE);} });
        }
    }

    static class SeriesVH extends RecyclerView.ViewHolder {
        ImageView poster; TextView title; TextView meta; MaterialButton btnFillMissing; MaterialButton btnServersAll; MaterialButton btnMetaAll;
        SeriesVH(@NonNull View itemView) {
            super(itemView);
            poster=itemView.findViewById(R.id.series_poster);
            title=itemView.findViewById(R.id.series_title);
            meta=itemView.findViewById(R.id.series_meta);
            btnFillMissing=itemView.findViewById(R.id.btn_fill_missing);
            btnServersAll=itemView.findViewById(R.id.btn_servers_all);
            btnMetaAll=itemView.findViewById(R.id.btn_metadata_all);
        }
        void bind(SeriesGroup g, ItemActionsListener l) {
            title.setText(g.seriesTitle);
            meta.setText("Seasons: "+g.seasons+" • Episodes: "+g.episodes);
            String url=g.posterUrl; if (url==null||url.isEmpty()) poster.setImageResource(R.drawable.ic_tv); else Glide.with(itemView.getContext()).load(url).placeholder(R.drawable.ic_tv).error(R.drawable.ic_tv).centerCrop().into(poster);
            btnFillMissing.setOnClickListener(v -> { if (l!=null) l.onFillMissingForSeries(g.seriesTitle, g.tmdbId); });
            btnServersAll.setOnClickListener(v -> { if (l!=null) l.onGenerateServersForSeries(g.seriesTitle); });
            btnMetaAll.setOnClickListener(v -> { if (l!=null) l.onUpdateMetadataForSeries(g.seriesTitle, g.tmdbId); });
        }
    }

    private static Integer readInt(TextInputEditText et){ try{ String s = et.getText()!=null?et.getText().toString().trim():""; if(!s.isEmpty()) return Integer.parseInt(s);}catch(Exception ignored){} return null; }

    private static String extractSeriesTitle(String episodeTitle){ if(episodeTitle==null||episodeTitle.trim().isEmpty()) return "Unknown Series"; String t=episodeTitle.trim(); t=t.replaceAll("\\s+S\\d+E\\d+.*$",""); t=t.replaceAll("\\s+Season\\s+\\d+.*$",""); if(t.matches(".*\\s+-\\s+.*")){ String[] p=t.split("\\s+-\\s+",2); if(p.length>1){ String f=p[0].trim(); if(f.length()>3) t=f; } } t=t.replaceAll("\\s+(Episode|Ep)\\s+\\d+.*$",""); t=t.replaceAll("\\s+\\d+x\\d+.*$",""); return t.trim(); }
}
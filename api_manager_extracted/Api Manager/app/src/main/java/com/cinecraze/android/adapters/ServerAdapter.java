package com.cinecraze.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ServerConfig;
import java.util.ArrayList;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
    
    private List<ServerConfig> servers;
    private OnServerChangeListener listener;

    public interface OnServerChangeListener {
        void onServerEnabledChanged(ServerConfig server, boolean enabled);
        void onServerQualityChanged(ServerConfig server, String quality);
        
        // Default implementation for backward compatibility
        default void onServerConfigChanged(ServerConfig server, boolean enabled) {
            onServerEnabledChanged(server, enabled);
        }
    }

    public ServerAdapter() {
        this.servers = new ArrayList<>();
    }

    public ServerAdapter(List<ServerConfig> servers, OnServerChangeListener listener) {
        this.servers = servers != null ? new ArrayList<>(servers) : new ArrayList<>();
        this.listener = listener;
    }

    public void setOnServerChangeListener(OnServerChangeListener listener) {
        this.listener = listener;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers.clear();
        if (servers != null) {
            this.servers.addAll(servers);
        }
        notifyDataSetChanged();
    }

    public void addServer(ServerConfig server) {
        this.servers.add(server);
        notifyItemInserted(servers.size() - 1);
    }

    public void removeServer(ServerConfig server) {
        int position = servers.indexOf(server);
        if (position != -1) {
            servers.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateServerStatus(String serverName, boolean isOnline) {
        for (int i = 0; i < servers.size(); i++) {
            ServerConfig server = servers.get(i);
            if (server.getName().equals(serverName)) {
                server.setOnline(isOnline);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public List<ServerConfig> getEnabledServers() {
        List<ServerConfig> enabledServers = new ArrayList<>();
        for (ServerConfig server : servers) {
            if (server.isEnabled()) {
                enabledServers.add(server);
            }
        }
        return enabledServers;
    }

    public void enableAllServers(boolean enable) {
        for (ServerConfig server : servers) {
            server.setEnabled(enable);
        }
        notifyDataSetChanged();
    }

    public void enableRecommendedServers() {
        List<String> recommended = new ArrayList<>();
        recommended.add("VidSrc");
        recommended.add("VidJoy");
        recommended.add("MultiEmbed");
        recommended.add("AutoEmbed.cc");
        recommended.add("VidSrc.to");

        for (ServerConfig server : servers) {
            server.setEnabled(recommended.contains(server.getName()));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_server_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServerConfig server = servers.get(position);
        holder.bind(server);
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private CheckBox enabledCheckBox;
        private TextView serverNameText;
        private TextView statusText;
        private TextView qualityText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            enabledCheckBox = itemView.findViewById(R.id.enabled_checkbox);
            serverNameText = itemView.findViewById(R.id.server_name_text);
            statusText = itemView.findViewById(R.id.status_text);
            qualityText = itemView.findViewById(R.id.quality_text);

            enabledCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    ServerConfig server = servers.get(position);
                    server.setEnabled(isChecked);
                    listener.onServerEnabledChanged(server, isChecked);
                }
            });
        }

        public void bind(ServerConfig server) {
            serverNameText.setText(server.getDisplayName());
            enabledCheckBox.setChecked(server.isEnabled());
            
            // Set status text and color
            String status = server.getStatusText();
            statusText.setText(status);
            
            if (status.equals("Online")) {
                statusText.setTextColor(itemView.getContext().getResources().getColor(R.color.success));
            } else if (status.equals("Offline")) {
                statusText.setTextColor(itemView.getContext().getResources().getColor(R.color.error));
            } else {
                statusText.setTextColor(itemView.getContext().getResources().getColor(R.color.text_secondary));
            }
            
            qualityText.setText(server.getQuality());
        }
    }
}
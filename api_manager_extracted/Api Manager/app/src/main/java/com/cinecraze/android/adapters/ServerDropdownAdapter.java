package com.cinecraze.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.cinecraze.android.R;
import com.cinecraze.android.models.ServerConfig;
import java.util.ArrayList;
import java.util.List;

public class ServerDropdownAdapter extends ArrayAdapter<ServerConfig> {
    
    private List<ServerConfig> servers;
    private OnServerSelectionListener listener;
    private Context context;

    public interface OnServerSelectionListener {
        void onServerSelectionChanged(ServerConfig server, boolean selected);
        void onSelectionChanged(List<ServerConfig> selectedServers);
    }

    public ServerDropdownAdapter(@NonNull Context context, @NonNull List<ServerConfig> servers) {
        super(context, 0, servers);
        this.context = context;
        this.servers = new ArrayList<>(servers);
    }

    public void setOnServerSelectionListener(OnServerSelectionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }
    
    @Override
    public int getCount() {
        return servers.size();
    }
    
    @Override
    public ServerConfig getItem(int position) {
        return servers.get(position);
    }

    private View createItemView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_server_dropdown, parent, false);
        }

        ServerConfig server = getItem(position);
        if (server != null) {
            CheckBox checkBox = convertView.findViewById(R.id.server_checkbox);
            TextView serverName = convertView.findViewById(R.id.server_name);
            TextView serverStatus = convertView.findViewById(R.id.server_status);

            serverName.setText(server.getDisplayName());
            serverStatus.setText(server.getStatusText());
            
            // Set status color
            if (server.getStatusText().equals("Online")) {
                serverStatus.setTextColor(context.getResources().getColor(R.color.success));
            } else if (server.getStatusText().equals("Offline")) {
                serverStatus.setTextColor(context.getResources().getColor(R.color.error));
            } else {
                serverStatus.setTextColor(context.getResources().getColor(R.color.text_secondary));
            }

            checkBox.setChecked(server.isEnabled());
            
            // Set click listener for the entire item
            convertView.setOnClickListener(v -> {
                boolean newState = !server.isEnabled();
                server.setEnabled(newState);
                checkBox.setChecked(newState);
                
                if (listener != null) {
                    listener.onServerSelectionChanged(server, newState);
                    listener.onSelectionChanged(getSelectedServers());
                }
            });

            // Set click listener for checkbox
            checkBox.setOnClickListener(v -> {
                boolean newState = checkBox.isChecked();
                server.setEnabled(newState);
                
                if (listener != null) {
                    listener.onServerSelectionChanged(server, newState);
                    listener.onSelectionChanged(getSelectedServers());
                }
            });
        }

        return convertView;
    }

    public List<ServerConfig> getSelectedServers() {
        List<ServerConfig> selected = new ArrayList<>();
        for (ServerConfig server : servers) {
            if (server.isEnabled()) {
                selected.add(server);
            }
        }
        return selected;
    }

    public void updateServers(List<ServerConfig> newServers) {
        this.servers.clear();
        this.servers.addAll(newServers);
        notifyDataSetChanged();
    }

    public String getSelectedServersText() {
        List<ServerConfig> selected = getSelectedServers();
        if (selected.isEmpty()) {
            return "No servers selected";
        } else if (selected.size() == 1) {
            return selected.get(0).getDisplayName();
        } else {
            return selected.size() + " servers selected";
        }
    }
}
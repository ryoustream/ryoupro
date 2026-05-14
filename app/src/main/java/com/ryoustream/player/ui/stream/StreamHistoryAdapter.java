package com.ryoustream.player.ui.stream;

import android.view.*;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.ryoustream.player.R;
import com.ryoustream.player.domain.model.NetworkStream;

/**
 * Adapter for stream history list.
 */
public class StreamHistoryAdapter extends ListAdapter<NetworkStream, StreamHistoryAdapter.ViewHolder> {

    public interface OnStreamClickListener { void onClick(NetworkStream stream); }
    public interface OnStreamDeleteListener { void onDelete(NetworkStream stream); }

    private final OnStreamClickListener clickListener;
    private final OnStreamDeleteListener deleteListener;

    public StreamHistoryAdapter(OnStreamClickListener click, OnStreamDeleteListener delete) {
        super(DIFF_CALLBACK);
        this.clickListener = click;
        this.deleteListener = delete;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stream, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, deleteListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvUrl;
        private final TextView tvProtocol;
        private final ImageButton btnDelete;

        ViewHolder(@NonNull View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tv_stream_title);
            tvUrl = view.findViewById(R.id.tv_stream_url);
            tvProtocol = view.findViewById(R.id.tv_protocol);
            btnDelete = view.findViewById(R.id.btn_delete);
        }

        void bind(NetworkStream stream, OnStreamClickListener click, OnStreamDeleteListener delete) {
            tvTitle.setText(stream.getTitle());
            tvUrl.setText(stream.getUrl());
            tvProtocol.setText(stream.getProtocol());
            itemView.setOnClickListener(v -> click.onClick(stream));
            btnDelete.setOnClickListener(v -> delete.onDelete(stream));
        }
    }

    private static final DiffUtil.ItemCallback<NetworkStream> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<NetworkStream>() {
                @Override
                public boolean areItemsTheSame(@NonNull NetworkStream a, @NonNull NetworkStream b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull NetworkStream a, @NonNull NetworkStream b) {
                    return a.getUrl().equals(b.getUrl());
                }
            };
}

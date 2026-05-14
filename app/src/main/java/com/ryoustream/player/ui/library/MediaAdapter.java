package com.ryoustream.player.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ryoustream.player.R;
import com.ryoustream.player.domain.model.MediaItem;

/**
 * RecyclerView adapter for media items supporting grid and list layouts.
 */
public class MediaAdapter extends ListAdapter<MediaItem, MediaAdapter.MediaViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(MediaItem item);
    }

    private final OnItemClickListener listener;
    private boolean isGridMode = true;

    public MediaAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setGridMode(boolean grid) {
        this.isGridMode = grid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = isGridMode ? R.layout.item_media_grid : R.layout.item_media_list;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView title;
        private final TextView duration;
        private final TextView info;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_thumbnail);
            title = itemView.findViewById(R.id.tv_title);
            duration = itemView.findViewById(R.id.tv_duration);
            info = itemView.findViewById(R.id.tv_info);
        }

        void bind(MediaItem item, OnItemClickListener listener) {
            title.setText(item.getTitle());
            duration.setText(item.getFormattedDuration());

            if (info != null) {
                StringBuilder infoText = new StringBuilder();
                if (item.getWidth() > 0 && item.getHeight() > 0) {
                    infoText.append(item.getWidth()).append("x").append(item.getHeight()).append(" · ");
                }
                infoText.append(item.getFormattedSize());
                info.setText(infoText.toString());
            }

            // Load thumbnail using Glide
            Glide.with(itemView.getContext())
                    .load(item.getUri())
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(thumbnail);

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }

    private static final DiffUtil.ItemCallback<MediaItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
                    return oldItem.getId() == newItem.getId()
                            && oldItem.getDateModified() == newItem.getDateModified();
                }
            };
}

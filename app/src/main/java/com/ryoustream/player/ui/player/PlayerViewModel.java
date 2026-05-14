package com.ryoustream.player.ui.player;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.domain.repository.MediaRepository;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for PlayerActivity. Manages current media state and position persistence.
 */
@HiltViewModel
public class PlayerViewModel extends ViewModel {

    private final MediaRepository repository;
    private final MutableLiveData<MediaItem> currentMediaItem = new MutableLiveData<>();
    private final MutableLiveData<Float> playbackSpeed = new MutableLiveData<>(1.0f);
    private long currentMediaId = -1;

    @Inject
    public PlayerViewModel(MediaRepository repository) {
        this.repository = repository;
    }

    public LiveData<MediaItem> getCurrentMediaItem() {
        return currentMediaItem;
    }

    public LiveData<Float> getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void openMedia(Uri uri) {
        // Build a transient MediaItem from URI for external opens
        MediaItem item = new MediaItem.Builder()
                .id(uri.hashCode())
                .title(uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "Unknown")
                .displayName(uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "")
                .uri(uri)
                .path(uri.getPath() != null ? uri.getPath() : "")
                .type(MediaItem.Type.VIDEO)
                .build();
        currentMediaId = item.getId();
        currentMediaItem.setValue(item);
    }

    public void openMediaItem(MediaItem item) {
        currentMediaId = item.getId();
        currentMediaItem.setValue(item);
    }

    public long getLastPosition(long mediaId) {
        return repository.getLastPosition(mediaId);
    }

    public void savePosition(long position) {
        if (currentMediaId >= 0) {
            repository.updateLastPlayed(currentMediaId, position);
        }
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed.setValue(speed);
    }
}

package com.ryoustream.player.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.domain.repository.MediaRepository;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for HomeFragment.
 */
@HiltViewModel
public class HomeViewModel extends ViewModel {

    private final MediaRepository repository;

    @Inject
    public HomeViewModel(MediaRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<MediaItem>> getRecentVideos() {
        return repository.getRecentVideos(20);
    }

    public LiveData<Boolean> isScanning() {
        return repository.isScanning();
    }

    public void loadData() {
        repository.scanMedia();
    }
}

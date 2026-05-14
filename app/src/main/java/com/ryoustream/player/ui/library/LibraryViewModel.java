package com.ryoustream.player.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.domain.repository.MediaRepository;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for LibraryFragment.
 */
@HiltViewModel
public class LibraryViewModel extends ViewModel {

    private final MediaRepository repository;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    @Inject
    public LibraryViewModel(MediaRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<MediaItem>> getMediaItems() {
        return Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.trim().isEmpty()) {
                return repository.getAllVideos();
            } else {
                return repository.searchVideos(query.trim());
            }
        });
    }

    public LiveData<Boolean> isScanning() {
        return repository.isScanning();
    }

    public void search(String query) {
        searchQuery.setValue(query);
    }

    public void refresh() {
        repository.scanMedia();
    }
}

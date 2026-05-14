package com.ryoustream.player.ui.stream;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.ryoustream.player.domain.model.NetworkStream;
import com.ryoustream.player.domain.repository.MediaRepository;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for network stream management.
 */
@HiltViewModel
public class NetworkStreamViewModel extends ViewModel {

    private final MediaRepository repository;

    @Inject
    public NetworkStreamViewModel(MediaRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<NetworkStream>> getStreams() {
        return repository.getAllStreams();
    }

    public void addAndPlayStream(String url, String title) {
        String protocol = NetworkStream.detectProtocol(url);
        NetworkStream stream = new NetworkStream(0, title, url, protocol,
                System.currentTimeMillis(), System.currentTimeMillis());
        repository.addStream(stream);
    }

    public void deleteStream(long id) {
        repository.deleteStream(id);
    }
}

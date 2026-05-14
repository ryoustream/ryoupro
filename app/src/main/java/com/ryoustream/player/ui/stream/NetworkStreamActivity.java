package com.ryoustream.player.ui.stream;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.ryoustream.player.R;
import com.ryoustream.player.databinding.ActivityNetworkStreamBinding;
import com.ryoustream.player.domain.model.NetworkStream;
import com.ryoustream.player.ui.player.PlayerActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for adding and playing network streams (HLS, RTSP, HTTP, etc.)
 */
@AndroidEntryPoint
public class NetworkStreamActivity extends AppCompatActivity {

    private ActivityNetworkStreamBinding binding;
    private NetworkStreamViewModel viewModel;
    private StreamHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNetworkStreamBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.network_stream);
        }

        viewModel = new ViewModelProvider(this).get(NetworkStreamViewModel.class);

        setupRecyclerView();
        setupButtons();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new StreamHistoryAdapter(
                stream -> openStream(stream.getUrl()),
                stream -> confirmDelete(stream)
        );
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnPlay.setOnClickListener(v -> {
            String url = binding.etUrl.getText() != null
                    ? binding.etUrl.getText().toString().trim() : "";
            if (url.isEmpty()) {
                binding.urlLayout.setError(getString(R.string.error_url_empty));
                return;
            }
            binding.urlLayout.setError(null);
            viewModel.addAndPlayStream(url, url);
            openStream(url);
        });
    }

    private void observeViewModel() {
        viewModel.getStreams().observe(this, streams -> {
            adapter.submitList(streams);
            binding.emptyHistory.setVisibility(
                    streams == null || streams.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openStream(String url) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void confirmDelete(NetworkStream stream) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_stream)
                .setMessage(getString(R.string.delete_stream_confirm, stream.getTitle()))
                .setPositiveButton(R.string.delete, (d, w) -> viewModel.deleteStream(stream.getId()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}

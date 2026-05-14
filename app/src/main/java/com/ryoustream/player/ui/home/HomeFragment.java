package com.ryoustream.player.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.ryoustream.player.databinding.FragmentHomeBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Home screen showing recent and quick-access media.
 */
@AndroidEntryPoint
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupRecent();
        observeViewModel();
        viewModel.loadData();
    }

    private void setupRecent() {
        binding.rvRecent.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
    }

    private void observeViewModel() {
        viewModel.getRecentVideos().observe(getViewLifecycleOwner(), items -> {
            binding.emptyState.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvRecent.setVisibility(items != null && !items.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.isScanning().observe(getViewLifecycleOwner(), scanning -> {
            binding.scanProgress.setVisibility(scanning ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

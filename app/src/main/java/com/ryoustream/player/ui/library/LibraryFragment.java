package com.ryoustream.player.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.ryoustream.player.R;
import com.ryoustream.player.databinding.FragmentLibraryBinding;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.ui.player.PlayerActivity;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment displaying video/audio library in grid or list mode.
 */
@AndroidEntryPoint
public class LibraryFragment extends Fragment {

    private static final int GRID_SPAN = 2;

    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private MediaAdapter adapter;
    private boolean isGridMode = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);

        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new MediaAdapter(item -> openPlayer(item));
        binding.recyclerView.setAdapter(adapter);
        setLayoutMode(true);
    }

    private void setLayoutMode(boolean grid) {
        isGridMode = grid;
        if (grid) {
            binding.recyclerView.setLayoutManager(
                    new GridLayoutManager(requireContext(), GRID_SPAN));
        } else {
            binding.recyclerView.setLayoutManager(
                    new LinearLayoutManager(requireContext()));
        }
        adapter.setGridMode(grid);
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
        });
    }

    private void observeViewModel() {
        viewModel.getMediaItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.swipeRefresh.setRefreshing(false);
            binding.emptyState.setVisibility(
                    items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.isScanning().observe(getViewLifecycleOwner(), scanning -> {
            if (!scanning) binding.swipeRefresh.setRefreshing(false);
        });
    }

    private void openPlayer(MediaItem item) {
        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        intent.setData(item.getUri());
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_library, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_view) {
            setLayoutMode(!isGridMode);
            item.setIcon(isGridMode ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
            return true;
        } else if (id == R.id.action_sort) {
            // Show sort dialog
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

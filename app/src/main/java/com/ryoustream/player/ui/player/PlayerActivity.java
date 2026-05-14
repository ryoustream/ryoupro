package com.ryoustream.player.ui.player;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.ryoustream.player.R;
import com.ryoustream.player.databinding.ActivityPlayerBinding;
import com.ryoustream.player.util.TimeUtils;

import dagger.hilt.android.AndroidEntryPoint;
import java.util.concurrent.TimeUnit;

/**
 * Full-featured player activity with gesture controls, PiP, and media controls.
 */
@AndroidEntryPoint
public class PlayerActivity extends AppCompatActivity {

    private static final int CONTROLS_HIDE_DELAY_MS = 3000;
    private static final int SEEK_STEP_MS = 10_000; // 10 seconds

    private ActivityPlayerBinding binding;
    private PlayerViewModel viewModel;
    private ExoPlayer player;
    private GestureDetector gestureDetector;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isControlsVisible = true;
    private boolean isLocked = false;

    private final Runnable hideControlsRunnable = () -> {
        if (!isLocked) hideControls();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindowFlags();
        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        setupPlayer();
        setupGestures();
        setupControls();
        observeViewModel();

        Uri mediaUri = getIntent().getData();
        if (mediaUri != null) {
            viewModel.openMedia(mediaUri);
        }
    }

    private void setupWindowFlags() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true)
                .build();

        binding.playerView.setPlayer(player);
        binding.playerView.setControllerAutoShow(false);
        binding.playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    // managed manually
                });

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                binding.progressBar.setVisibility(
                        state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_ENDED) onPlaybackEnded();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                binding.btnPlayPause.setImageResource(
                        isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                if (isPlaying) scheduleHideControls();
            }
        });

        viewModel.getCurrentMediaItem().observe(this, item -> {
            if (item == null) return;
            MediaItem exoItem = MediaItem.fromUri(item.getUri());
            player.setMediaItem(exoItem);
            player.prepare();
            player.play();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(item.getTitle());
            }
            // Restore last position
            long lastPos = viewModel.getLastPosition(item.getId());
            if (lastPos > 5000) player.seekTo(lastPos);
        });
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                float x = e.getX();
                float screenWidth = binding.playerView.getWidth();
                if (x < screenWidth / 3f) {
                    seekRelative(-SEEK_STEP_MS);
                    showSeekFeedback(-10);
                } else if (x > screenWidth * 2 / 3f) {
                    seekRelative(SEEK_STEP_MS);
                    showSeekFeedback(10);
                } else {
                    togglePlayPause();
                }
                return true;
            }
        });

        binding.playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void setupControls() {
        binding.btnPlayPause.setOnClickListener(v -> togglePlayPause());
        binding.btnBack.setOnClickListener(v -> onBackPressed());
        binding.btnRotate.setOnClickListener(v -> toggleOrientation());
        binding.btnLock.setOnClickListener(v -> toggleLock());
        binding.btnPip.setOnClickListener(v -> enterPictureInPicture());

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long newPos = (long) progress * player.getDuration() / 100;
                    binding.tvCurrentTime.setText(TimeUtils.formatDuration(newPos));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    long newPos = (long) seekBar.getProgress() * player.getDuration() / 100;
                    player.seekTo(newPos);
                }
                scheduleHideControls();
            }
        });

        // Update seek bar periodically
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateSeekBar();
                handler.postDelayed(this, 500);
            }
        });
    }

    private void observeViewModel() {
        // Observer pattern for ViewModel state
    }

    private void updateSeekBar() {
        if (player == null || player.getDuration() <= 0) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        int progress = (int) (pos * 100 / dur);
        binding.seekBar.setProgress(progress);
        binding.tvCurrentTime.setText(TimeUtils.formatDuration(pos));
        binding.tvTotalTime.setText(TimeUtils.formatDuration(dur));
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        scheduleHideControls();
    }

    private void seekRelative(long offsetMs) {
        if (player == null) return;
        long newPos = Math.max(0, player.getCurrentPosition() + offsetMs);
        player.seekTo(Math.min(newPos, player.getDuration()));
    }

    private void showSeekFeedback(int seconds) {
        String msg = seconds > 0 ? "+" + seconds + "s" : seconds + "s";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toggleControls() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        isControlsVisible = true;
        binding.controlsContainer.setVisibility(View.VISIBLE);
        binding.controlsContainer.animate().alpha(1f).setDuration(200).start();
        scheduleHideControls();
    }

    private void hideControls() {
        isControlsVisible = false;
        binding.controlsContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> binding.controlsContainer.setVisibility(View.GONE))
                .start();
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        if (player != null && player.isPlaying()) {
            handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
        }
    }

    private void toggleOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void toggleLock() {
        isLocked = !isLocked;
        binding.btnLock.setImageResource(
                isLocked ? R.drawable.ic_lock : R.drawable.ic_lock_open);
        if (isLocked) {
            handler.removeCallbacks(hideControlsRunnable);
            hideControls();
        }
    }

    private void enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            builder.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(builder.build());
        }
    }

    private void onPlaybackEnded() {
        handler.removeCallbacks(hideControlsRunnable);
        showControls();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiP, @NonNull Configuration config) {
        super.onPictureInPictureModeChanged(isInPiP, config);
        binding.controlsContainer.setVisibility(isInPiP ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            viewModel.savePosition(player.getCurrentPosition());
            if (!isInPictureInPictureMode()) {
                player.pause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}

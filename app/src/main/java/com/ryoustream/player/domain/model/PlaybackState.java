package com.ryoustream.player.domain.model;

/**
 * Domain model representing the current playback state.
 */
public class PlaybackState {
    public enum State {
        IDLE, BUFFERING, READY, PLAYING, PAUSED, ENDED, ERROR
    }

    private final State state;
    private final long position;
    private final long duration;
    private final float speed;
    private final boolean isRepeat;
    private final String errorMessage;

    public PlaybackState(State state, long position, long duration, float speed,
                         boolean isRepeat, String errorMessage) {
        this.state = state;
        this.position = position;
        this.duration = duration;
        this.speed = speed;
        this.isRepeat = isRepeat;
        this.errorMessage = errorMessage;
    }

    public State getState() { return state; }
    public long getPosition() { return position; }
    public long getDuration() { return duration; }
    public float getSpeed() { return speed; }
    public boolean isRepeat() { return isRepeat; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean isBuffering() { return state == State.BUFFERING; }
    public boolean isEnded() { return state == State.ENDED; }
    public boolean hasError() { return state == State.ERROR; }

    public int getProgressPercent() {
        if (duration <= 0) return 0;
        return (int) ((position * 100) / duration);
    }
}

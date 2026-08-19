package otto.djgun.djcraft.client.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.playback.DJPlaylistSequencer;
import otto.djgun.djcraft.playback.DJPlaybackMode;

public final class DJPlaylistState {
    private final RandomGenerator random;
    private DJPlaybackMode mode = DJPlaybackMode.SEQUENTIAL;
    private List<DiscPlaybackReference> entries = List.of();
    private DJPlaylistSequencer sequencer;
    private int currentIndex = -1;
    private int pendingIndex = -1;
    private long pendingSinceMs;
    private long activeSessionId;
    private long completedSessionId;

    public DJPlaylistState() {
        this(new Random());
    }

    public DJPlaylistState(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public DJPlaybackMode mode() {
        return mode;
    }

    public void setMode(DJPlaybackMode mode) {
        DJPlaybackMode next = Objects.requireNonNull(mode);
        if (this.mode == next) {
            return;
        }
        this.mode = next;
        if (sequencer != null) {
            sequencer.setMode(next);
        }
    }

    public Selection configureAndPlan(List<DiscPlaybackReference> playlist, int startIndex, long nowMs) {
        if (playlist == null || playlist.isEmpty() || startIndex < 0 || startIndex >= playlist.size()) {
            throw new IllegalArgumentException("Invalid DJ playlist selection");
        }
        entries = List.copyOf(playlist);
        sequencer = new DJPlaylistSequencer(entries, random);
        sequencer.setMode(mode);
        currentIndex = -1;
        activeSessionId = 0L;
        completedSessionId = 0L;
        return setPending(startIndex, nowMs);
    }

    public boolean confirmStarted(long sessionId, String trackId, UUID discId) {
        if (pendingIndex < 0 || !entries.get(pendingIndex).trackId().equals(trackId)) {
            clearPlayback();
            activeSessionId = sessionId;
            return false;
        }
        entries = new ArrayList<>(entries);
        entries.set(pendingIndex, entries.get(pendingIndex).withDiscId(discId));
        entries = List.copyOf(entries);
        sequencer.select(pendingIndex);
        currentIndex = pendingIndex;
        clearPending();
        activeSessionId = sessionId;
        completedSessionId = 0L;
        return true;
    }

    public Optional<Selection> naturalEnd(long sessionId, long nowMs) {
        if (sessionId == 0L || sessionId != activeSessionId || sessionId == completedSessionId) {
            return Optional.empty();
        }
        completedSessionId = sessionId;
        activeSessionId = 0L;
        if (entries.isEmpty() || currentIndex < 0) {
            return Optional.empty();
        }
        sequencer.next();
        int nextIndex = sequencer.currentIndex();
        return Optional.of(setPending(nextIndex, nowMs));
    }

    public void stop(long sessionId) {
        if (sessionId == 0L || sessionId == activeSessionId) {
            clearPlayback();
        }
    }

    public boolean expirePending(long nowMs, long timeoutMs) {
        if (pendingIndex >= 0 && nowMs - pendingSinceMs >= timeoutMs) {
            clearPending();
            return true;
        }
        return false;
    }

    public void clearPlayback() {
        entries = List.of();
        sequencer = null;
        currentIndex = -1;
        activeSessionId = 0L;
        completedSessionId = 0L;
        clearPending();
    }

    public boolean hasPending() {
        return pendingIndex >= 0;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public List<DiscPlaybackReference> entries() {
        return entries;
    }

    private Selection setPending(int index, long nowMs) {
        pendingIndex = index;
        pendingSinceMs = nowMs;
        return new Selection(index, entries.get(index));
    }

    private void clearPending() {
        pendingIndex = -1;
        pendingSinceMs = 0L;
    }

    public record Selection(int index, DiscPlaybackReference disc) {
        public String trackId() {
            return disc.trackId();
        }
    }
}

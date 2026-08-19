package otto.djgun.djcraft.playback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

import otto.djgun.djcraft.data.DiscPlaybackReference;

/**
 * Side-safe playlist ordering used by server-authoritative network groups.
 */
public final class DJPlaylistSequencer {
    private final RandomGenerator random;
    private final List<Integer> shuffleBag = new ArrayList<>();
    private List<DiscPlaybackReference> entries = List.of();
    private DJPlaybackMode mode = DJPlaybackMode.SEQUENTIAL;
    private int currentIndex = -1;
    private boolean shuffleInitialized;

    public DJPlaylistSequencer(List<DiscPlaybackReference> entries) {
        this(entries, new Random());
    }

    public DJPlaylistSequencer(List<DiscPlaybackReference> entries, RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
        setEntries(entries);
    }

    public void setEntries(List<DiscPlaybackReference> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("DJ playlist cannot be empty");
        }
        this.entries = List.copyOf(entries);
        currentIndex = -1;
        shuffleBag.clear();
        shuffleInitialized = false;
    }

    public List<DiscPlaybackReference> entries() {
        return entries;
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
        shuffleBag.clear();
        shuffleInitialized = false;
        if (next == DJPlaybackMode.SHUFFLE && currentIndex >= 0) {
            refillShuffleBag(false);
            shuffleInitialized = true;
        }
    }

    public int currentIndex() {
        return currentIndex;
    }

    public DiscPlaybackReference select(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException("Invalid DJ playlist index");
        }
        currentIndex = index;
        if (mode == DJPlaybackMode.SHUFFLE && !shuffleInitialized) {
            refillShuffleBag(false);
            shuffleInitialized = true;
        }
        return entries.get(index);
    }

    public DiscPlaybackReference next() {
        if (currentIndex < 0) {
            return select(0);
        }
        int nextIndex = switch (mode) {
            case SEQUENTIAL -> (currentIndex + 1) % entries.size();
            case REPEAT_ONE -> currentIndex;
            case SHUFFLE -> nextShuffleIndex();
        };
        return select(nextIndex);
    }

    private int nextShuffleIndex() {
        if (shuffleBag.isEmpty()) {
            refillShuffleBag(true);
        }
        int chosenPosition = shuffleBag.size() - 1;
        String currentTrack = entries.get(currentIndex).trackId();
        if (entries.stream().anyMatch(entry -> !entry.trackId().equals(currentTrack))) {
            for (int position = shuffleBag.size() - 1; position >= 0; position--) {
                if (!entries.get(shuffleBag.get(position)).trackId().equals(currentTrack)) {
                    chosenPosition = position;
                    break;
                }
            }
        }
        return shuffleBag.remove(chosenPosition);
    }

    private void refillShuffleBag(boolean includeCurrent) {
        shuffleBag.clear();
        for (int index = 0; index < entries.size(); index++) {
            if (includeCurrent || index != currentIndex) {
                shuffleBag.add(index);
            }
        }
        for (int index = shuffleBag.size() - 1; index > 0; index--) {
            Collections.swap(shuffleBag, index, random.nextInt(index + 1));
        }
    }
}

package otto.djgun.djcraft.session;

import java.util.concurrent.TimeUnit;

/**
 * One monotonic server clock shared by every member session for one group track.
 */
public final class GroupPlaybackClock implements DJServerClock {
    private final long startTimeNs;

    public GroupPlaybackClock() {
        this(0L);
    }

    public GroupPlaybackClock(long initialPositionMs) {
        startTimeNs = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(Math.max(0L, initialPositionMs));
    }

    @Override
    public long currentTimeMs() {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startTimeNs));
    }
}

package otto.djgun.djcraft.session;

import java.util.concurrent.TimeUnit;

final class StandaloneDJServerClock implements DJServerClock {
    private final long startTimeNs;
    private long pausedTimeNs;
    private long pauseStartedNs = -1L;

    StandaloneDJServerClock() {
        this(0L);
    }

    StandaloneDJServerClock(long initialPositionMs) {
        startTimeNs = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(Math.max(0L, initialPositionMs));
    }

    @Override
    public long currentTimeMs() {
        long now = pauseStartedNs >= 0L ? pauseStartedNs : System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, now - startTimeNs - pausedTimeNs));
    }

    @Override
    public void setPaused(boolean paused) {
        long now = System.nanoTime();
        if (paused && pauseStartedNs < 0L) {
            pauseStartedNs = now;
        } else if (!paused && pauseStartedNs >= 0L) {
            pausedTimeNs += now - pauseStartedNs;
            pauseStartedNs = -1L;
        }
    }

    @Override
    public boolean isPaused() {
        return pauseStartedNs >= 0L;
    }

    @Override
    public void alignBackward(long correctionMs) {
        pausedTimeNs += TimeUnit.MILLISECONDS.toNanos(Math.max(0L, correctionMs));
    }
}

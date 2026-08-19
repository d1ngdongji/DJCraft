package otto.djgun.djcraft.sound;

/** Pure timing math shared by the sound-thread binding path and unit tests. */
public final class DJAudioTiming {
    private DJAudioTiming() {
    }

    public static long calculateSeekPositionMs(long initialPositionMs, long estimatedTransitMs,
            long localLoadElapsedMs, long playbackStartMs, long totalDurationMs) {
        long lowerBound = Math.max(0L, playbackStartMs);
        long target = saturatingAdd(Math.max(initialPositionMs, lowerBound),
                saturatingAdd(Math.max(0L, estimatedTransitMs), Math.max(0L, localLoadElapsedMs)));
        if (totalDurationMs <= lowerBound) {
            return lowerBound;
        }
        return Math.max(lowerBound, Math.min(target, totalDurationMs - 1L));
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}

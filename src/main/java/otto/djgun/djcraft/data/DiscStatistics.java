package otto.djgun.djcraft.data;

/** Persistent, per-disc play statistics. */
public record DiscStatistics(int maxCombo, long totalPlayTimeMs) {
    public static final DiscStatistics EMPTY = new DiscStatistics(0, 0L);

    public DiscStatistics {
        maxCombo = Math.max(0, maxCombo);
        totalPlayTimeMs = Math.max(0L, totalPlayTimeMs);
    }

    public DiscStatistics merge(DiscStatistics other) {
        if (other == null) {
            return this;
        }
        return new DiscStatistics(Math.max(maxCombo, other.maxCombo),
                Math.max(totalPlayTimeMs, other.totalPlayTimeMs));
    }

    public boolean isGilded(int totalBeatCount) {
        return totalBeatCount > 0 && maxCombo >= gildedThreshold(totalBeatCount);
    }

    public static int gildedThreshold(int totalBeatCount) {
        if (totalBeatCount <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) (((long) totalBeatCount * 4L + 4L) / 5L);
    }
}

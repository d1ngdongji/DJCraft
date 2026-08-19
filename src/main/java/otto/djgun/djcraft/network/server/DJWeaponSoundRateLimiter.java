package otto.djgun.djcraft.network.server;

import java.util.HashMap;
import java.util.Map;

/** Token bucket plus monotonic sequence guard, kept independent of Minecraft for deterministic tests. */
public final class DJWeaponSoundRateLimiter<K> {
    private final double maxTokens;
    private final double tokensPerTick;
    private final Map<K, Bucket> buckets = new HashMap<>();

    public DJWeaponSoundRateLimiter(double maxTokens, double tokensPerTick) {
        if (!Double.isFinite(maxTokens) || !Double.isFinite(tokensPerTick)
                || maxTokens <= 0.0 || tokensPerTick <= 0.0) {
            throw new IllegalArgumentException("Invalid token bucket settings");
        }
        this.maxTokens = maxTokens;
        this.tokensPerTick = tokensPerTick;
    }

    public boolean accept(K key, long tick, long sequence) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(maxTokens, tick, 0));
        if (sequence <= bucket.lastSequence) {
            return false;
        }
        bucket.tokens = Math.min(maxTokens,
                bucket.tokens + Math.max(0, tick - bucket.lastTick) * tokensPerTick);
        bucket.lastTick = tick;
        bucket.lastSequence = sequence;
        if (bucket.tokens < 1.0) {
            return false;
        }
        bucket.tokens -= 1.0;
        return true;
    }

    public void remove(K key) {
        buckets.remove(key);
    }

    public void clear() {
        buckets.clear();
    }

    private static final class Bucket {
        private double tokens;
        private long lastTick;
        private long lastSequence;

        private Bucket(double tokens, long lastTick, long lastSequence) {
            this.tokens = tokens;
            this.lastTick = lastTick;
            this.lastSequence = lastSequence;
        }
    }
}

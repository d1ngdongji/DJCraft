package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DJChargeReleaseCacheState<H, I> {
    private static final long TTL_TICKS = 4L;
    private final Map<UUID, PendingRelease<H, I>> pending = new HashMap<>();

    public void store(UUID playerId, long sessionId, long sequence, H hand, I item, HitResult result,
            boolean stopAfterAction, long gameTime) {
        pending.put(playerId, new PendingRelease<>(sessionId, sequence, hand, item, result, stopAfterAction,
                gameTime + TTL_TICKS));
    }

    public Decision consume(UUID playerId, long sessionId, H hand, I item, long gameTime) {
        PendingRelease<H, I> release = pending.remove(playerId);
        if (release == null || release.sessionId != sessionId || release.hand != hand || release.item != item
                || gameTime > release.expiresAtTick) {
            return null;
        }
        return new Decision(release.sequence, release.result, release.stopAfterAction);
    }

    public void cleanup(UUID playerId) {
        pending.remove(playerId);
    }

    public void cleanupExpired(long gameTime) {
        pending.values().removeIf(release -> gameTime > release.expiresAtTick);
    }

    public void clear() {
        pending.clear();
    }

    public record Decision(long sequence, HitResult result, boolean stopAfterAction) {
    }

    private record PendingRelease<H, I>(long sessionId, long sequence, H hand, I item, HitResult result,
            boolean stopAfterAction, long expiresAtTick) {
    }
}

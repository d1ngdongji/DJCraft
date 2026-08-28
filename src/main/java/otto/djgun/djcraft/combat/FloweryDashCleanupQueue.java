package otto.djgun.djcraft.combat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Defers reentrant player cleanup until the active Flowery dash tick finishes. */
final class FloweryDashCleanupQueue {
    private final Set<UUID> pending = new HashSet<>();
    private boolean ticking;

    void beginTick() {
        if (ticking) {
            throw new IllegalStateException("Flowery dashes are already ticking");
        }
        ticking = true;
    }

    void remove(UUID playerId, Map<UUID, ?> active) {
        if (ticking) {
            pending.add(playerId);
        } else {
            active.remove(playerId);
        }
    }

    void endTick(Map<UUID, ?> active) {
        ticking = false;
        pending.forEach(active::remove);
        pending.clear();
    }

    void clear() {
        pending.clear();
        ticking = false;
    }
}

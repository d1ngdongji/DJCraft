package otto.djgun.djcraft.combat;

import java.util.HashSet;
import java.util.Set;

/** Small deterministic state machine separated from server entities for regression tests. */
final class DJMeleeWindowState {
    private final boolean area;
    private final long expiresAtTick;
    private final Set<Integer> contactedEntityIds = new HashSet<>();
    private boolean closed;

    DJMeleeWindowState(boolean area, long expiresAtTick) {
        this.area = area;
        this.expiresAtTick = expiresAtTick;
    }

    boolean acceptContact(int entityId) {
        if (closed || !contactedEntityIds.add(entityId)) {
            return false;
        }
        if (!area) {
            closed = true;
        }
        return true;
    }

    boolean shouldFinish(long gameTime) {
        return closed || gameTime >= expiresAtTick;
    }
}

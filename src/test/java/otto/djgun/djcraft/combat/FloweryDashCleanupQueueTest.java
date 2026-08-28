package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FloweryDashCleanupQueueTest {
    @Test
    void lethalContactCleanupDoesNotInvalidateDashIterator() {
        UUID attackerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<UUID, Object> active = new HashMap<>();
        active.put(attackerId, new Object());
        active.put(targetId, new Object());
        FloweryDashCleanupQueue cleanup = new FloweryDashCleanupQueue();

        cleanup.beginTick();
        Iterator<Map.Entry<UUID, Object>> dashes = active.entrySet().iterator();
        dashes.next();
        cleanup.remove(targetId, active);

        assertTrue(active.containsKey(targetId));
        assertDoesNotThrow(dashes::remove);
        cleanup.endTick(active);
        assertFalse(active.containsKey(targetId));
    }

    @Test
    void cleanupOutsideTickRemovesImmediately() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, Object> active = new HashMap<>();
        active.put(playerId, new Object());

        new FloweryDashCleanupQueue().remove(playerId, active);

        assertFalse(active.containsKey(playerId));
    }
}

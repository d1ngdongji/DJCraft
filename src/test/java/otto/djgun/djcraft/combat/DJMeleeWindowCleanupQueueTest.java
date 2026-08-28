package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DJMeleeWindowCleanupQueueTest {
    @Test
    void cleanupDuringTickDoesNotInvalidateActiveIterator() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, Object> active = new HashMap<>();
        active.put(playerId, new Object());
        DJMeleeWindowCleanupQueue cleanup = new DJMeleeWindowCleanupQueue();

        cleanup.beginTick();
        Iterator<Map.Entry<UUID, Object>> players = active.entrySet().iterator();
        players.next();
        cleanup.remove(playerId, active);

        assertTrue(active.containsKey(playerId));
        assertDoesNotThrow(players::remove);
        cleanup.endTick(active);
        assertFalse(active.containsKey(playerId));
    }

    @Test
    void cleanupOutsideTickRemovesImmediately() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, Object> active = new HashMap<>();
        active.put(playerId, new Object());

        new DJMeleeWindowCleanupQueue().remove(playerId, active);

        assertFalse(active.containsKey(playerId));
    }
}

package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DJDeferredDamageQueueTest {

    @Test
    void drainsDueEntriesInArrivalOrderAndLeavesFutureEntriesQueued() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 1200L, "first");
        queue.add(7L, 1200L, "second");
        queue.add(7L, 1500L, "future");

        assertTrue(queue.drainReady(7L, 1199L).isEmpty());
        assertEquals(List.of("first", "second"), queue.drainReady(7L, 1200L));
        assertEquals(1, queue.size());
    }

    @Test
    void canReleaseAnEarlierDueEntryWithoutBlockingOnArrivalOrder() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 2000L, "long-window");
        queue.add(7L, 1600L, "short-window");

        assertEquals(List.of("short-window"), queue.drainReady(7L, 1600L));
        assertEquals(1, queue.size());
    }

    @Test
    void sessionReplacementOrMissingSessionReleasesEverything() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 5000L, "old-one");
        queue.add(7L, 6000L, "old-two");

        assertEquals(List.of("old-one", "old-two"), queue.drainReady(8L, 0L));
        assertTrue(queue.isEmpty());

        queue.add(8L, 7000L, "stopped");
        assertEquals(List.of("stopped"), queue.drainReady(null, 0L));
        assertTrue(queue.isEmpty());
    }

    @Test
    void clearDiscardsPendingEntriesForDeathOrLogout() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 1200L, "discarded");

        queue.clear();

        assertTrue(queue.isEmpty());
        assertTrue(queue.drainReady(null, 0L).isEmpty());
    }

    @Test
    void parryDrainReleasesOnlyTheActiveSessionInFifoOrder() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 5000L, "first");
        queue.add(8L, 5000L, "other-session");
        queue.add(7L, 6000L, "second");

        assertEquals(List.of("first", "second"), queue.drainSession(7L, ignored -> true));
        assertFalse(queue.hasSession(7L));
        assertTrue(queue.hasSession(8L));
        assertEquals(1, queue.size());
        assertEquals(List.of("other-session"), queue.drainSession(8L, ignored -> true));
    }

    @Test
    void parryDrainLeavesNonMatchingDamageQueuedForNormalRelease() {
        DJDeferredDamageQueue<String> queue = new DJDeferredDamageQueue<>();
        queue.add(7L, 5000L, "front");
        queue.add(7L, 5000L, "behind");
        queue.add(8L, 5000L, "other-session");

        assertEquals(List.of("front"), queue.drainSession(7L, "front"::equals));
        assertTrue(queue.hasSession(7L));
        assertEquals(2, queue.size());
        assertEquals(List.of("behind", "other-session"), queue.drainReady(7L, 5000L));
    }
}

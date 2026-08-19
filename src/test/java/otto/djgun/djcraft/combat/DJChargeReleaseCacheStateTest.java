package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DJChargeReleaseCacheStateTest {

    @Test
    void bindsSessionAndConsumesOnlyOnce() {
        var state = new DJChargeReleaseCacheState<Object, Object>();
        UUID playerId = UUID.randomUUID();
        Object hand = new Object();
        Object item = new Object();
        state.store(playerId, 42L, 7L, hand, item, hit(), false, 100L);

        assertNull(state.consume(playerId, 41L, hand, item, 101L));

        state.store(playerId, 42L, 8L, hand, item, hit(), false, 100L);
        var decision = state.consume(playerId, 42L, hand, item, 104L);
        assertNotNull(decision);
        assertTrue(decision.result().isHit());
        assertTrue(decision.sequence() == 8L);
        assertNull(state.consume(playerId, 42L, hand, item, 104L));
    }

    @Test
    void expiresAfterFourTicks() {
        var state = new DJChargeReleaseCacheState<Object, Object>();
        UUID playerId = UUID.randomUUID();
        Object hand = new Object();
        Object item = new Object();
        state.store(playerId, 42L, 1L, hand, item, hit(), false, 100L);

        assertNull(state.consume(playerId, 42L, hand, item, 105L));
    }

    private static HitResult hit() {
        return new HitResult(true, null, null, 0, 0L);
    }
}

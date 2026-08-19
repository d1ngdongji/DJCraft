package otto.djgun.djcraft.combat.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJClientItemCooldownsTest {
    @Test
    void warmupOnlyReplacesAShorterRemainingCooldown() {
        long nowMs = 1_000L;

        assertTrue(DJClientItemCooldowns.shouldReplace(501L, 1_500L, nowMs));
        assertFalse(DJClientItemCooldowns.shouldReplace(500L, 1_500L, nowMs));
        assertFalse(DJClientItemCooldowns.shouldReplace(499L, 1_500L, nowMs));
        assertTrue(DJClientItemCooldowns.shouldReplace(1L, 999L, nowMs));
    }
}

package otto.djgun.djcraft.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJClientMovementStateTest {
    @Test
    void ignoresOldSessionsAndAppliesMatchingServerState() {
        var state = new DJClientMovementState(42L);
        assertFalse(state.apply(41L, 20, 2, 3));
        assertEquals(0, state.getDashCooldownTicks());
        assertEquals(0, state.getRemainingAirJumps());

        assertTrue(state.apply(42L, 20, 2, 3));
        assertEquals(20, state.getDashCooldownTicks());
        assertEquals(2, state.getConsecutiveDashes());
        assertEquals(3, state.getRemainingAirJumps());
    }

    @Test
    void predictionCountsDownAndAuthoritativeStateDoesNotAddNetworkDelay() {
        var state = new DJClientMovementState(7L);
        state.predictDash(1, 30);
        state.tick();
        state.tick();
        assertEquals(28, state.getDashCooldownTicks());

        state.apply(7L, 30, 0, 1);
        assertEquals(28, state.getDashCooldownTicks());
        state.apply(7L, 0, 0, 1);
        assertEquals(0, state.getDashCooldownTicks());
    }

    @Test
    void thirdPredictedDashStartsThirtyTickCooldown() {
        var state = new DJClientMovementState(8L);

        state.predictDash(3, 30);
        state.predictDash(3, 30);
        assertEquals(2, state.getConsecutiveDashes());
        assertEquals(0, state.getDashCooldownTicks());
        state.predictDash(3, 30);
        assertEquals(0, state.getConsecutiveDashes());
        assertEquals(30, state.getDashCooldownTicks());
    }

    @Test
    void airJumpPredictionCannotGoNegative() {
        var state = new DJClientMovementState(9L);
        state.apply(9L, 0, 0, 1);
        state.predictAirJumpUsed();
        state.predictAirJumpUsed();
        assertEquals(0, state.getRemainingAirJumps());
    }
}

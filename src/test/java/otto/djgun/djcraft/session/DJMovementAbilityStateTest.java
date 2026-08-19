package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJMovementAbilityStateTest {
    @Test
    void dashCooldownLastsExactlyConfiguredTicks() {
        var state = new DJMovementAbilityState(1);
        assertTrue(state.canDash(100L));

        state.startDashCooldown(100L, 30);
        assertEquals(30, state.dashCooldownTicks(100L));
        assertFalse(state.canDash(129L));
        assertEquals(1, state.dashCooldownTicks(129L));
        assertTrue(state.canDash(130L));
        assertEquals(0, state.dashCooldownTicks(130L));
    }

    @Test
    void thirdConsecutiveDashStartsCooldownAndResetsChain() {
        var state = new DJMovementAbilityState(1);

        assertFalse(state.recordDashAndMaybeStartCooldown(100L, 3, 30));
        assertEquals(1, state.consecutiveDashes());
        assertFalse(state.recordDashAndMaybeStartCooldown(101L, 3, 30));
        assertEquals(2, state.consecutiveDashes());
        assertTrue(state.recordDashAndMaybeStartCooldown(102L, 3, 30));
        assertEquals(0, state.consecutiveDashes());
        assertEquals(30, state.dashCooldownTicks(102L));
    }

    @Test
    void airJumpLimitOnlyResetsOnGround() {
        var state = new DJMovementAbilityState(1);
        assertEquals(1, state.remainingAirJumps());
        assertTrue(state.tryUseAirJump());
        assertFalse(state.tryUseAirJump());
        assertFalse(state.resetAirJumpsIfGrounded(false));
        assertEquals(0, state.remainingAirJumps());

        assertTrue(state.resetAirJumpsIfGrounded(true));
        assertEquals(1, state.remainingAirJumps());
    }

    @Test
    void zeroConfiguredAirJumpsDisablesAbility() {
        var state = new DJMovementAbilityState(0);
        assertFalse(state.tryUseAirJump());
        assertEquals(0, state.remainingAirJumps());
    }

    @Test
    void changingLimitPreservesAlreadyUsedAirJumps() {
        var state = new DJMovementAbilityState(1);
        assertTrue(state.tryUseAirJump());

        assertTrue(state.setMaxAirJumps(2));
        assertEquals(1, state.remainingAirJumps());
        assertTrue(state.tryUseAirJump());

        assertFalse(state.setMaxAirJumps(1));
        assertEquals(0, state.remainingAirJumps());
        assertFalse(state.tryUseAirJump());

        assertTrue(state.resetAirJumpsIfGrounded(true));
        assertEquals(1, state.remainingAirJumps());
    }
}

package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.util.BeatGridUtil;

class DJShieldStateTest {
    private static final Object SHIELD = new Object();

    @Test
    void authorizationRequiresMatchingHandItemAndFreshTick() {
        var state = new DJShieldState<String, Object>();
        state.authorizeStart("main", SHIELD, 7L, 100L, 600L, true, 0.2, 14L);

        assertTrue(state.takeStartAuthorization("main", SHIELD, 14L).isPresent());
        assertTrue(state.takeStartAuthorization("main", SHIELD, 14L).isEmpty());

        state.authorizeStart("main", SHIELD, 7L, 100L, 600L, true, 0.2, 14L);
        assertTrue(state.takeStartAuthorization("off", SHIELD, 13L).isEmpty());

        state.authorizeStart("main", SHIELD, 7L, 100L, 600L, true, 0.2, 14L);
        assertTrue(state.takeStartAuthorization("main", SHIELD, 15L).isEmpty());
    }

    @Test
    void parryAcceptsMultipleHitsButRewardsEnergyOnlyOnceAndExpiresAtExactBoundary() {
        var state = activeState(true, 1_000L, 2.0);

        var first = state.tryParry(999L).orElseThrow();
        assertEquals(7L, first.actionSequence());
        assertTrue(first.rewardEnergy());
        var second = state.tryParry(999L).orElseThrow();
        assertEquals(7L, second.actionSequence());
        assertFalse(second.rewardEnergy());

        state = activeState(true, 1_000L, 2.0);
        assertTrue(state.tryParry(1_000L).isEmpty());
    }

    @Test
    void missedJudgmentStillActivatesShieldWithoutParry() {
        var state = activeState(false, 1_000L, 2.0);

        assertTrue(state.isActiveFor("main", SHIELD));
        assertTrue(state.tryParry(500L).isEmpty());
    }

    @Test
    void sustainChargesCatchUpOneVirtualBeatAtATime() {
        var state = activeState(true, 1_000L, 2.0);

        assertFalse(state.isSustainChargeDue(2.99));
        assertTrue(state.isSustainChargeDue(3.0));
        state.recordSustainCharge(1.0);
        assertTrue(state.isSustainChargeDue(5.2));
        state.recordSustainCharge(1.0);
        assertTrue(state.isSustainChargeDue(5.2));
        state.recordSustainCharge(1.0);
        assertFalse(state.isSustainChargeDue(5.2));
    }

    @Test
    void virtualBeatDeadlineHandlesTempoChanges() {
        List<BeatEvent> beats = List.of(
                new BeatEvent(0, "beat"),
                new BeatEvent(500, "beat"),
                new BeatEvent(1_500, "beat"));
        double startBeat = BeatGridUtil.getVirtualBeat(250L, beats);
        long deadline = BeatGridUtil.calculateTargetTime(250L, beats, 1.0);

        assertTrue(Math.abs(startBeat - 0.5) < 1.0E-9);
        assertTrue(deadline == 1_000L);
    }

    @Test
    void clearingAfterFailedPaymentEndsActiveShield() {
        var state = activeState(true, 1_000L, 2.0);
        assertTrue(state.hasActiveShield());

        var end = state.finishActiveShield().orElseThrow();

        assertFalse(state.hasActiveShield());
        assertTrue(end.applyMissedParryCooldown());
        assertTrue(state.tryParry(500L).isEmpty());
    }

    @Test
    void successfulParryPreventsMissedParryCooldown() {
        var state = activeState(true, 1_000L, 2.0);
        assertTrue(state.tryParry(500L).isPresent());

        var end = state.finishActiveShield().orElseThrow();

        assertFalse(end.applyMissedParryCooldown());
        assertTrue(state.finishActiveShield().isEmpty());
    }

    private static DJShieldState<String, Object> activeState(boolean parry, long parryExpiresAt, double startBeat) {
        var state = new DJShieldState<String, Object>();
        state.authorizeStart("main", SHIELD, 7L, 0L, parryExpiresAt, parry, startBeat, 4L);
        var pending = state.takeStartAuthorization("main", SHIELD, 0L).orElseThrow();
        state.activate(pending, 1.0);
        return state;
    }
}

package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJSessionResourceStateTest {
    @Test
    void parryComboIncreaseDoesNotAddNormalHitEnergy() {
        var state = new DJSessionResourceState();

        state.confirmParry(10);
        state.confirmParry(10);
        assertEquals(2, state.getCombo());
        assertEquals(0.0, state.getEnergy());
    }

    @Test
    void startsEmptyAndRegeneratesAtBaseInterval() {
        var state = new DJSessionResourceState();

        assertEquals(0, state.getCombo());
        assertEquals(0, state.getCurrentTrackCombo());
        assertEquals(0.0, state.getEnergy());
        tick(state, 9, 100.0);
        assertEquals(0.0, state.getEnergy());
        assertTrue(state.tick(100.0));
        assertEquals(1.0, state.getEnergy());
    }

    @Test
    void restoredComboCarriesIntoANewSessionWithoutCreditingTheNewTrack() {
        var state = new DJSessionResourceState();

        state.restoreCombo(12, 20);
        assertEquals(12, state.getCombo());
        assertEquals(0, state.getCurrentTrackCombo());

        assertTrue(state.confirmHit(1L, 21, 100.0));
        assertEquals(13, state.getCombo());
        assertEquals(1, state.getCurrentTrackCombo());

        state.restoreCombo(13, 22);
        assertTrue(state.setCombo(13, 22));
        assertEquals(13, state.getCurrentTrackCombo());
    }

    @Test
    void comboBreakResetsBothGlobalAndCurrentTrackCombo() {
        var state = new DJSessionResourceState();
        state.restoreCombo(12, 20);
        state.confirmHit(1L, 21, 100.0);

        assertTrue(state.judgmentFailed(2L, 0));
        assertEquals(0, state.getCombo());
        assertEquals(0, state.getCurrentTrackCombo());
    }

    @Test
    void startsWithRetainedEnergyClampedToCurrentMaximum() {
        assertEquals(7.5, new DJSessionResourceState(7.5, 10.0).getEnergy());
        assertEquals(4.0, new DJSessionResourceState(7.5, 4.0).getEnergy());
        assertEquals(0.0, new DJSessionResourceState(-1.0, 10.0).getEnergy());
        assertEquals(0.0, new DJSessionResourceState(Double.NaN, 10.0).getEnergy());
    }

    @Test
    void administratorSettersClampValuesAndRefreshComboIdleTracking() {
        var state = new DJSessionResourceState();

        assertTrue(state.setCombo(12, 20));
        assertEquals(12, state.getCombo());
        assertEquals(12, state.getCurrentTrackCombo());
        assertFalse(state.onBeat(24));
        assertTrue(state.onBeat(25));
        assertEquals(0, state.getCombo());

        assertFalse(state.setCombo(-1, 30));
        assertEquals(0, state.getCombo());
        assertTrue(state.setEnergy(80.0, 50.0));
        assertEquals(50.0, state.getEnergy());
        assertTrue(state.setEnergy(-1.0, 50.0));
        assertEquals(0.0, state.getEnergy());
    }

    @Test
    void comboSetBeforeTheFirstBeatStillExpiresAfterIdleGrace() {
        var state = new DJSessionResourceState();

        assertTrue(state.setCombo(3, -1));
        assertFalse(state.onBeat(4));
        assertTrue(state.onBeat(5));
    }

    @Test
    void comboSaturatesInsteadOfOverflowingAfterAdministratorSetsMaximum() {
        var state = new DJSessionResourceState();

        assertTrue(state.setCombo(Integer.MAX_VALUE, 10));
        assertTrue(state.confirmCombo(1L, 11));
        assertEquals(Integer.MAX_VALUE, state.getCombo());
    }

    @Test
    void confirmedHitsUsePostIncrementTierAndDeduplicateSequences() {
        var state = new DJSessionResourceState();

        for (long sequence = 1; sequence <= 4; sequence++) {
            assertTrue(state.confirmHit(sequence, (int) sequence, 100.0));
        }
        assertEquals(4, state.getCombo());
        assertEquals(4.0, state.getEnergy());

        assertTrue(state.confirmHit(5L, 5, 100.0));
        assertEquals(5, state.getCombo());
        assertEquals(5.0, state.getEnergy());
        assertFalse(state.confirmHit(5L, 5, 100.0));
        assertEquals(5.0, state.getEnergy());

        for (long sequence = 6; sequence <= 10; sequence++) {
            state.confirmHit(sequence, (int) sequence, 100.0);
        }
        assertEquals(10, state.getCombo());
        assertEquals(11.0, state.getEnergy());
    }

    @Test
    void regenerationUsesContinuousTickCountAcrossTierChanges() {
        var state = new DJSessionResourceState();

        tick(state, 9, 100.0);
        confirmThrough(state, 1, 5);
        state.tick(100.0);
        assertEquals(6.0, state.getEnergy());

        tick(state, 4, 100.0);
        confirmThrough(state, 6, 10);
        state.tick(100.0);
        assertEquals(13.0, state.getEnergy());
    }

    @Test
    void missResetsComboWithoutRemovingEnergyAndRejectsOlderHits() {
        var state = new DJSessionResourceState();
        state.confirmHit(1L, 10, 100.0);

        assertTrue(state.judgmentFailed(2L, 1));
        assertEquals(1, state.getCombo());
        assertEquals(0, state.getToleranceChances());
        assertEquals(1.0, state.getEnergy());
        assertFalse(state.confirmHit(1L, 11, 100.0));
        assertFalse(state.confirmHit(2L, 11, 100.0));
        assertTrue(state.confirmHit(3L, 11, 100.0));

        assertTrue(state.judgmentFailed(4L, 1));
        assertEquals(0, state.getCombo());
        assertEquals(2.0, state.getEnergy());
    }

    @Test
    void oneActionSequenceCanOnlyAddOneComboAcrossMultipleTargets() {
        var state = new DJSessionResourceState();

        assertTrue(state.confirmHit(42L, 8, 100.0));
        assertFalse(state.confirmHit(42L, 8, 100.0));
        assertFalse(state.confirmHit(42L, 8, 100.0));
        assertEquals(1, state.getCombo());
        assertEquals(1.0, state.getEnergy());
    }

    @Test
    void tridentProjectileDamageCanIncrementComboForEveryDamageInstance() {
        var state = new DJSessionResourceState();

        assertTrue(state.confirmProjectileDamage(42L, 8, 100.0));
        assertTrue(state.confirmProjectileDamage(42L, 8, 100.0));

        assertEquals(2, state.getCombo());
        assertEquals(1.0, state.getEnergy());
    }

    @Test
    void missSequencesAreDeduplicatedBeforeConsumingTolerance() {
        var state = new DJSessionResourceState(0.0, 100.0, 2, 0, 2);
        state.confirmHit(1L, 10, 100.0);

        assertTrue(state.judgmentFailed(2L, 2));
        assertEquals(1, state.getToleranceChances());
        assertFalse(state.judgmentFailed(2L, 2));
        assertFalse(state.judgmentFailed(1L, 2));
        assertEquals(1, state.getToleranceChances());
        assertEquals(1, state.getCombo());
    }

    @Test
    void toleranceRestoresOnTheConfiguredTickBoundary() {
        var state = new DJSessionResourceState();
        state.judgmentFailed(1L, 1);

        tick(state, 99, 100.0, 1, 100);
        assertEquals(0, state.getToleranceChances());
        state.tick(100.0, 1, 100);
        assertEquals(1, state.getToleranceChances());
        assertEquals(0, state.getToleranceRechargeProgress());
    }

    @Test
    void multipleToleranceChancesRestoreOnePerIntervalWithoutExceedingMaximum() {
        var state = new DJSessionResourceState(0.0, 100.0, 2, 0, 2);
        state.judgmentFailed(1L, 2);
        state.judgmentFailed(2L, 2);

        tick(state, 100, 100.0, 2, 100);
        assertEquals(1, state.getToleranceChances());
        tick(state, 100, 100.0, 2, 100);
        assertEquals(2, state.getToleranceChances());
        tick(state, 100, 100.0, 2, 100);
        assertEquals(2, state.getToleranceChances());
    }

    @Test
    void defaultsProvideTwoToleranceChancesAndRechargeEveryEightyTicks() {
        var state = new DJSessionResourceState();

        assertEquals(2, state.getToleranceChances());
        assertTrue(state.judgmentFailed(1L));
        assertTrue(state.judgmentFailed(2L));
        assertEquals(0, state.getToleranceChances());
        tick(state, 79, 100.0);
        assertEquals(0, state.getToleranceChances());
        state.tick(100.0);
        assertEquals(1, state.getToleranceChances());
    }

    @Test
    void retainedRechargeProgressContinuesInANewSession() {
        var firstSession = new DJSessionResourceState();
        firstSession.judgmentFailed(1L, 1);
        tick(firstSession, 40, 100.0, 1, 100);

        var nextSession = new DJSessionResourceState(firstSession.getEnergy(), 100.0,
                firstSession.getToleranceChances(), firstSession.getToleranceRechargeProgress(), 1);
        tick(nextSession, 59, 100.0, 1, 100);
        assertEquals(0, nextSession.getToleranceChances());
        nextSession.tick(100.0, 1, 100);
        assertEquals(1, nextSession.getToleranceChances());
    }

    @Test
    void toleranceClampsWhenConfiguredMaximumShrinksOrIsDisabled() {
        var state = new DJSessionResourceState(0.0, 100.0, 2, 50, 2);

        state.tick(100.0, 1, 100);
        assertEquals(1, state.getToleranceChances());
        assertEquals(0, state.getToleranceRechargeProgress());
        state.tick(100.0, 0, 100);
        assertEquals(0, state.getToleranceChances());
        assertEquals(0, state.getToleranceRechargeProgress());
    }

    @Test
    void fifthIdleBeatResetsComboWithoutRemovingEnergy() {
        var state = new DJSessionResourceState();
        state.confirmHit(1L, 10, 100.0);

        assertFalse(state.onBeat(14));
        assertTrue(state.onBeat(15));
        assertEquals(0, state.getCombo());
        assertEquals(1.0, state.getEnergy());
        assertEquals(2, state.getToleranceChances());
    }

    @Test
    void successfulJudgmentsWithoutDamageExcludeOnlyTheirBeatFromIdleTracking() {
        var state = new DJSessionResourceState();
        state.confirmHit(1L, 10, 100.0);

        assertFalse(state.onBeat(14));
        state.ignoreBeatForComboReset(14);
        state.ignoreBeatForComboReset(14);
        assertFalse(state.onBeat(15));
        assertTrue(state.onBeat(16));
        assertEquals(0, state.getCombo());
    }

    @Test
    void extendedIdleGraceResetsComboOnTheSeventhBeat() {
        var state = new DJSessionResourceState();
        state.confirmHit(1L, 10, 100.0);

        assertFalse(state.onBeat(15, 7));
        assertFalse(state.onBeat(16, 7));
        assertTrue(state.onBeat(17, 7));
        assertEquals(0, state.getCombo());
    }

    @Test
    void nonAttackableBeatsDoNotAdvanceIdleComboReset() {
        var state = new DJSessionResourceState();
        state.confirmHit(1L, 10, 100.0);

        assertFalse(state.onBeat(11, false, 5));
        assertFalse(state.onBeat(12, true, 5));
        assertFalse(state.onBeat(13, false, 5));
        assertFalse(state.onBeat(14, true, 5));
        assertFalse(state.onBeat(15, true, 5));
        assertFalse(state.onBeat(16, true, 5));
        assertTrue(state.onBeat(17, true, 5));
        assertEquals(0, state.getCombo());
    }

    @Test
    void energyClampsToCurrentMaximumIncludingDynamicReductionAndZero() {
        var state = new DJSessionResourceState();
        confirmThrough(state, 1, 10);
        assertEquals(11.0, state.getEnergy());

        assertTrue(state.tick(4.0));
        assertEquals(4.0, state.getEnergy());
        assertTrue(state.tick(0.0));
        assertEquals(0.0, state.getEnergy());

        tick(state, 5, 0.0);
        assertEquals(0.0, state.getEnergy());
    }

    @Test
    void energyConsumptionIsAtomicAndRejectsInvalidAmounts() {
        var state = new DJSessionResourceState();
        tick(state, 30, 100.0);
        assertEquals(3.0, state.getEnergy());

        assertFalse(state.tryConsumeEnergy(3.01));
        assertEquals(3.0, state.getEnergy());
        assertFalse(state.tryConsumeEnergy(-1.0));
        assertFalse(state.tryConsumeEnergy(Double.NaN));
        assertTrue(state.tryConsumeEnergy(3.0));
        assertEquals(0.0, state.getEnergy());
    }

    @Test
    void grantedEnergyClampsToMaximumAndRejectsInvalidAmounts() {
        var state = new DJSessionResourceState();

        assertTrue(state.grantEnergy(5.0, 4.0));
        assertEquals(4.0, state.getEnergy());
        assertFalse(state.grantEnergy(5.0, 4.0));
        assertFalse(state.grantEnergy(-1.0, 100.0));
        assertFalse(state.grantEnergy(Double.NaN, 100.0));
        assertEquals(4.0, state.getEnergy());
    }

    @Test
    void fillEnergyTracksTheCurrentMaximum() {
        var state = new DJSessionResourceState(2.0, 10.0);

        assertTrue(state.fillEnergy(10.0));
        assertEquals(10.0, state.getEnergy());
        assertFalse(state.fillEnergy(10.0));
        assertTrue(state.fillEnergy(4.0));
        assertEquals(4.0, state.getEnergy());
        assertTrue(state.fillEnergy(Double.NaN));
        assertEquals(0.0, state.getEnergy());
    }

    private static void confirmThrough(DJSessionResourceState state, long first, long last) {
        for (long sequence = first; sequence <= last; sequence++) {
            state.confirmHit(sequence, (int) sequence, 100.0);
        }
    }

    private static void tick(DJSessionResourceState state, int count, double maxEnergy) {
        for (int i = 0; i < count; i++) {
            state.tick(maxEnergy);
        }
    }

    private static void tick(DJSessionResourceState state, int count, double maxEnergy,
            int maxToleranceChances, int rechargeTicks) {
        for (int i = 0; i < count; i++) {
            state.tick(maxEnergy, maxToleranceChances, rechargeTicks);
        }
    }
}

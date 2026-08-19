package otto.djgun.djcraft.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DJClientSessionResourceStateTest {
    @Test
    void appliesOnlyMatchingSessionSnapshotsAndClampsValues() {
        DJClientSessionResourceState state = new DJClientSessionResourceState(42L, 100.0);

        state.apply(41L, 7, 30.0, 100.0);
        assertEquals(0, state.getCombo());
        assertEquals(0.0, state.getEnergy());

        state.apply(42L, 7, 120.0, 80.0);
        assertEquals(7, state.getCombo());
        assertEquals(80.0, state.getEnergy());
        assertEquals(80.0, state.getMaxEnergy());
    }

    @Test
    void appliesToleranceSnapshotAndPredictsProtectedThenUnprotectedMisses() {
        DJClientSessionResourceState state = new DJClientSessionResourceState(42L, 100.0);

        state.apply(42L, 7, 30.0, 100.0, 1, 1);
        state.predictMiss();
        assertEquals(7, state.getCombo());
        assertEquals(0, state.getToleranceChances());

        state.predictMiss();
        assertEquals(0, state.getCombo());
        assertEquals(0, state.getToleranceChances());
        assertEquals(1, state.getMaxToleranceChances());
    }

    @Test
    void clampsToleranceValuesAndIgnoresOtherSessions() {
        DJClientSessionResourceState state = new DJClientSessionResourceState(42L, 100.0);

        state.apply(41L, 3, 0.0, 100.0, 8, 8);
        assertEquals(2, state.getToleranceChances());
        assertEquals(2, state.getMaxToleranceChances());

        state.apply(42L, 3, 0.0, 100.0, 8, 2);
        assertEquals(2, state.getToleranceChances());
        assertEquals(2, state.getMaxToleranceChances());
    }
}

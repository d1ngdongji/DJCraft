package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJSessionValidationStateTest {
    @Test
    void rejectsReplayedAndOutOfOrderActions() {
        DJSessionValidationState state = new DJSessionValidationState();

        assertTrue(state.acceptActionSequence(1));
        assertFalse(state.acceptActionSequence(1));
        assertFalse(state.acceptActionSequence(0));
        assertTrue(state.acceptActionSequence(2));
    }

    @Test
    void stopsAfterFiveConsecutiveClockAnomaliesWithoutRejectingAnIndividualAudit() {
        DJSessionValidationState state = new DJSessionValidationState();
        assertFalse(state.auditClock(1000, 0, 50).aligned());
        state.synchronizeClock(1000, 0);

        for (int i = 1; i <= 4; i++) {
            var audit = state.auditClock(2000 + i, 5000 + i, 50);
            assertTrue(audit.anomalous());
            assertFalse(audit.stopSession());
        }
        assertTrue(state.auditClock(2005, 5005, 50).stopSession());

        assertFalse(state.auditClock(2100, 1100, 50).anomalous());
        assertFalse(state.auditClock(2101, 5101, 50).stopSession());
    }
}

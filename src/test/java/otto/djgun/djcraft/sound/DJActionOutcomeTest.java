package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJActionOutcomeTest {
    @Test
    void beatAndTargetOutcomesRemainIndependent() {
        var fired = new DJActionOutcome(BeatOutcome.HIT, TargetOutcome.UNKNOWN);
        var confirmed = new DJActionOutcome(BeatOutcome.HIT, TargetOutcome.HIT);
        var missedBeat = new DJActionOutcome(BeatOutcome.MISS, TargetOutcome.NOT_APPLICABLE);

        assertTrue(fired.successful());
        assertEquals(TargetOutcome.UNKNOWN, fired.target());
        assertEquals(TargetOutcome.HIT, confirmed.target());
        assertFalse(missedBeat.successful());
    }

    @Test
    void notJudgedIsNotReportedAsBeatMiss() {
        assertEquals(BeatOutcome.NOT_APPLICABLE, DJActionOutcome.NOT_JUDGED.beat());
        assertEquals(TargetOutcome.NOT_APPLICABLE, DJActionOutcome.NOT_JUDGED.target());
        assertTrue(DJActionOutcome.NOT_JUDGED.successful());
    }
}

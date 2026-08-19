package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import java.util.List;
import otto.djgun.djcraft.data.BeatEvent;

class DJAutoChargeTimingTest {
    @Test
    void schedulesExactlyOneContinuousVirtualBeatAfterPress() {
        List<BeatEvent> beats = List.of(beat(0), beat(500), beat(1_500));
        DJAutoChargeTiming.Schedule schedule = DJAutoChargeTiming.schedule(250L, beats, 1);
        assertEquals(0.5, schedule.startVirtualBeat(), 1.0E-9);
        assertEquals(1.5, schedule.targetVirtualBeat(), 1.0E-9);
        assertEquals(1_000L, schedule.targetTimeMs());
        assertTrue(DJAutoChargeTiming.isSchedulable(schedule, 0.6, 2_000, 0));
        assertFalse(DJAutoChargeTiming.isSchedulable(schedule, 1.5, 2_000, 0));
        assertFalse(DJAutoChargeTiming.isSchedulable(schedule, 0.6, 1_000, 0));
        assertTrue(DJAutoChargeTiming.isDue(1.5, schedule.targetVirtualBeat()));
        assertFalse(DJAutoChargeTiming.isDue(1.49, schedule.targetVirtualBeat()));
    }

    @Test
    void progressUsesBeatTimelineAndClamps() {
        assertEquals(0.0F, DJAutoChargeTiming.progress(0.9, 1.0, 2.0));
        assertEquals(0.5F, DJAutoChargeTiming.progress(1.5, 1.0, 2.0));
        assertEquals(1.0F, DJAutoChargeTiming.progress(2.1, 1.0, 2.0));
        assertEquals(0.0F, DJAutoChargeTiming.progress(1.0, 1.0, 1.0));
    }

    private static BeatEvent beat(int timeMs) {
        return new BeatEvent(timeMs, "beat", null);
    }
}

package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJAudioTimingTest {
    @Test
    void seekIncludesTransportAndLocalBufferDelay() {
        assertEquals(1_850L, DJAudioTiming.calculateSeekPositionMs(
                1_000L, 250L, 600L, 500L, 10_000L));
    }

    @Test
    void seekClampsToPlayableRawIntervalAndForcesTailCatchUp() {
        assertEquals(500L, DJAudioTiming.calculateSeekPositionMs(0L, 0L, 0L, 500L, 10_000L));
        assertEquals(9_999L, DJAudioTiming.calculateSeekPositionMs(
                9_900L, 1_000L, 5_000L, 500L, 10_000L));
    }

    @Test
    void exactInstancePolicyRejectsUnrelatedStoppedAndStaleSounds() {
        Object expected = new Object();
        assertTrue(DJAudioBindingPolicy.shouldBind(expected, expected, true, false));
        assertFalse(DJAudioBindingPolicy.shouldBind(expected, new Object(), true, false));
        assertFalse(DJAudioBindingPolicy.shouldBind(expected, expected, false, false));
        assertFalse(DJAudioBindingPolicy.shouldBind(expected, expected, true, true));
    }
}

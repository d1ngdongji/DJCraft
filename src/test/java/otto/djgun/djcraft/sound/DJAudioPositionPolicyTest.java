package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJAudioPositionPolicyTest {
    @Test
    void rejectsOpenALResetToZeroAtNaturalEnd() {
        assertTrue(DJAudioPositionPolicy.isDiscontinuousBackwardJump(248_116L, 0L));
    }

    @Test
    void acceptsInitialAndForwardPositions() {
        assertFalse(DJAudioPositionPolicy.isDiscontinuousBackwardJump(0L, 0L));
        assertFalse(DJAudioPositionPolicy.isDiscontinuousBackwardJump(248_116L, 248_127L));
    }

    @Test
    void toleratesSmallOpenALJitter() {
        assertFalse(DJAudioPositionPolicy.isDiscontinuousBackwardJump(10_000L, 9_900L));
        assertTrue(DJAudioPositionPolicy.isDiscontinuousBackwardJump(10_000L, 9_899L));
    }
}

package otto.djgun.djcraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DJCrosshairAnimationTest {
    @Test
    void linesKickOutAndEaseBackWithinTheLegacyDuration() {
        assertEquals(6.0f, DJCrosshairAnimation.lineOffset(0L), 0.001f);
        assertEquals(1.5f, DJCrosshairAnimation.lineOffset(125L), 0.001f);
        assertEquals(0.0f, DJCrosshairAnimation.lineOffset(250L), 0.001f);
        assertEquals(0.0f, DJCrosshairAnimation.lineOffset(-1L), 0.001f);
    }
}

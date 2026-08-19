package otto.djgun.djcraft.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DJComboHudAnimationTest {
    @Test
    void bounceStartsLargeAndSettlesAtRest() {
        DJComboHudAnimation.Frame start = DJComboHudAnimation.sample(0L);
        DJComboHudAnimation.Frame middle = DJComboHudAnimation.sample(160L);
        DJComboHudAnimation.Frame end = DJComboHudAnimation.sample(320L);

        assertEquals(1.42f, start.scale(), 0.001f);
        assertEquals(0.0f, start.bounceY(), 0.001f);
        assertTrue(middle.bounceY() < -5.9f);
        assertEquals(1.0f, end.scale(), 0.001f);
        assertEquals(0.0f, end.bounceY(), 0.001f);
    }

    @Test
    void effectThresholdsIncludeFiveAndTen() {
        assertFalse(DJComboHudAnimation.hasGlow(4));
        assertTrue(DJComboHudAnimation.hasGlow(5));
        assertFalse(DJComboHudAnimation.hasTrail(9));
        assertTrue(DJComboHudAnimation.hasTrail(10));
        assertFalse(DJComboHudAnimation.hasRainbowTrail(99));
        assertTrue(DJComboHudAnimation.hasRainbowTrail(100));
    }

    @Test
    void rainbowTrailCyclesHueAndSeparatesEchoes() {
        DJComboHudAnimation.Rgb red = DJComboHudAnimation.rainbowTrailColor(0L, 0L);
        DJComboHudAnimation.Rgb blue = DJComboHudAnimation.rainbowTrailColor(900L, 0L);
        DJComboHudAnimation.Rgb olderEcho = DJComboHudAnimation.rainbowTrailColor(900L, 72L);

        assertEquals(1.0f, red.red(), 0.001f);
        assertEquals(0.10f, red.green(), 0.001f);
        assertEquals(0.10f, red.blue(), 0.001f);
        assertEquals(0.10f, blue.red(), 0.001f);
        assertEquals(0.19f, blue.green(), 0.001f);
        assertEquals(1.0f, blue.blue(), 0.001f);
        assertTrue(Math.abs(blue.red() - olderEcho.red()) > 0.1f
                || Math.abs(blue.green() - olderEcho.green()) > 0.1f
                || Math.abs(blue.blue() - olderEcho.blue()) > 0.1f);
    }

    @Test
    void trailMovesRightWhileFadingQuickly() {
        assertEquals(0.0f, DJComboHudAnimation.trailOffsetX(0L), 0.001f);
        assertEquals(0.68f, DJComboHudAnimation.trailAlpha(0L), 0.001f);
        assertTrue(DJComboHudAnimation.trailOffsetX(90L) > 13.0f);
        assertTrue(DJComboHudAnimation.trailAlpha(90L) > 0.20f);
        assertEquals(18.0f, DJComboHudAnimation.trailOffsetX(180L), 0.001f);
        assertEquals(0.0f, DJComboHudAnimation.trailAlpha(180L), 0.001f);
    }

    @Test
    void adjacentDigitsHaveDifferentWavePhases() {
        float first = DJComboHudAnimation.waveOffsetY(1000L, 0, 10);
        float second = DJComboHudAnimation.waveOffsetY(1000L, 1, 10);
        assertTrue(Math.abs(first - second) > 0.1f);
    }

    @Test
    void sizeAndWaveAmplitudeGrowLogarithmicallyAfterFiftyCombo() {
        assertEquals(1.0f, DJComboHudAnimation.comboScale(49), 0.001f);
        assertEquals(1.0f, DJComboHudAnimation.comboScale(50), 0.001f);
        assertEquals(1.8f, DJComboHudAnimation.waveAmplitude(50), 0.001f);

        float scaleAtHundred = DJComboHudAnimation.comboScale(100);
        float scaleAtTwoHundred = DJComboHudAnimation.comboScale(200);
        float waveAtHundred = DJComboHudAnimation.waveAmplitude(100);
        float waveAtTwoHundred = DJComboHudAnimation.waveAmplitude(200);
        assertTrue(scaleAtHundred > 1.08f);
        assertTrue(scaleAtTwoHundred > scaleAtHundred);
        assertTrue(scaleAtTwoHundred - scaleAtHundred < scaleAtHundred - 1.0f);
        assertTrue(waveAtHundred > 2.15f);
        assertTrue(waveAtTwoHundred > waveAtHundred);
        assertTrue(waveAtTwoHundred - waveAtHundred < waveAtHundred - 1.8f);
    }

    @Test
    void disappearanceBranchesFollowDifferentCurvedRadialPaths() {
        assertEquals(0.0f, DJComboHudAnimation.disappearOffsetX(0L, 0), 0.001f);
        assertEquals(0.0f, DJComboHudAnimation.disappearOffsetY(0L, 0), 0.001f);
        float branchZeroX = DJComboHudAnimation.disappearOffsetX(260L, 0);
        float branchOneX = DJComboHudAnimation.disappearOffsetX(260L, 1);
        assertTrue(Math.abs(branchZeroX - branchOneX) > 1.0f);
        assertTrue(DJComboHudAnimation.disappearAlpha(260L) < 0.20f);
        assertEquals(0.0f, DJComboHudAnimation.disappearAlpha(520L), 0.001f);
    }
}

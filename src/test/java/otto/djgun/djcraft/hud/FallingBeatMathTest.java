package otto.djgun.djcraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.data.BeatPostJudgmentBehavior;

class FallingBeatMathTest {
    @Test
    void markerCenterStartsAtTopTouchesLineAndContinuesBelow() {
        assertEquals(-16.0f, FallingBeatMath.markerTopY(
                600, 2000, 1400, 200.0f, 32.0f), 0.001f);
        assertEquals(184.0f, FallingBeatMath.markerTopY(
                2000, 2000, 1400, 200.0f, 32.0f), 0.001f);
        assertEquals(284.0f, FallingBeatMath.markerTopY(
                2700, 2000, 1400, 200.0f, 32.0f), 0.001f);
    }

    @Test
    void earlyJudgmentKeepsTheActualMarkerPositionInsteadOfSnappingToPerfect() {
        float actualTop = FallingBeatMath.markerTopY(
                1900, 2000, 1400, 200.0f, 32.0f);
        float perfectTop = FallingBeatMath.markerTopY(
                2000, 2000, 1400, 200.0f, 32.0f);

        assertEquals(169.714f, actualTop, 0.001f);
        assertTrue(actualTop < perfectTop);
    }

    @Test
    void fillRunsFromPreviousBeatToTarget() {
        assertEquals(0.0f, FallingBeatMath.fillProgress(1000, 1000, 1500), 0.001f);
        assertEquals(0.5f, FallingBeatMath.fillProgress(1250, 1000, 1500), 0.001f);
        assertEquals(1.0f, FallingBeatMath.fillProgress(1500, 1000, 1500), 0.001f);
    }

    @Test
    void rotationSupportsReverseRpm() {
        assertEquals(180.0f, FallingBeatMath.rotationDegrees(500, 60.0f), 0.001f);
        assertEquals(180.0f, FallingBeatMath.rotationDegrees(500, -60.0f), 0.001f);
    }

    @Test
    void feedbackDurationsMatchTheVisualContract() {
        var held = FallingBeatMath.effectFrame(BeatPostJudgmentBehavior.FREEZE_DISSIPATE, 100);
        assertTrue(held.visible());
        assertEquals(1.0f, held.alpha(), 0.001f);

        var fading = FallingBeatMath.effectFrame(BeatPostJudgmentBehavior.FREEZE_DISSIPATE, 210);
        assertTrue(fading.visible());
        assertTrue(fading.alpha() < 1.0f);

        assertFalse(FallingBeatMath.effectFrame(BeatPostJudgmentBehavior.DISSIPATE, 220).visible());
        assertFalse(FallingBeatMath.effectFrame(BeatPostJudgmentBehavior.BOUNCE, 320).visible());
    }

    @Test
    void impactWaveStartsBrightExpandsAndEndsAtContractDuration() {
        var start = FallingBeatMath.impactWaveFrame(0L);
        var middle = FallingBeatMath.impactWaveFrame(160L);

        assertTrue(start.visible());
        assertEquals(10.0f, start.radius(), 0.001f);
        assertEquals(1.0f, start.flashAlpha(), 0.001f);
        assertTrue(middle.visible());
        assertTrue(middle.radius() > start.radius());
        assertTrue(middle.coreAlpha() > 0.0f);
        assertFalse(FallingBeatMath.impactWaveFrame(320L).visible());
    }
}

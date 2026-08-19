package otto.djgun.djcraft.hud;

import otto.djgun.djcraft.client.render.DJRainbowColor;

/** Pure animation math for the combo HUD. */
public final class DJComboHudAnimation {
    static final long BOUNCE_DURATION_MS = 320L;
    static final long TRAIL_LIFETIME_MS = 180L;
    static final long DISAPPEAR_LIFETIME_MS = 520L;
    private static final int COMBO_GROWTH_THRESHOLD = 50;
    private static final float COMBO_GROWTH_RANGE = 10.0f;
    private static final float SIZE_LOG_GROWTH = 0.15f;
    private static final float BASE_WAVE_AMPLITUDE = 1.8f;
    private static final float WAVE_LOG_GROWTH = 0.24f;

    private DJComboHudAnimation() {
    }

    static Frame sample(long elapsedMs) {
        float progress = Math.clamp((float) elapsedMs / BOUNCE_DURATION_MS, 0.0f, 1.0f);
        float remaining = 1.0f - progress;
        float scale = 1.0f + 0.42f * remaining * remaining;
        float bounceY = -6.0f * (float) Math.sin(Math.PI * progress);
        return new Frame(scale, bounceY);
    }

    static boolean hasGlow(int combo) {
        return combo >= 5;
    }

    static boolean hasTrail(int combo) {
        return combo >= 10;
    }

    static boolean hasRainbowTrail(int combo) {
        return combo >= 100;
    }

    static Rgb rainbowTrailColor(long nowMs, long ageMs) {
        DJRainbowColor.Rgb color = DJRainbowColor.sample(nowMs, ageMs * 5L);
        return new Rgb(color.red(), color.green(), color.blue());
    }

    static float trailProgress(long ageMs) {
        return Math.clamp((float) ageMs / TRAIL_LIFETIME_MS, 0.0f, 1.0f);
    }

    static float trailOffsetX(long ageMs) {
        float progress = trailProgress(ageMs);
        return 18.0f * (1.0f - (1.0f - progress) * (1.0f - progress));
    }

    static float trailAlpha(long ageMs) {
        float remaining = 1.0f - trailProgress(ageMs);
        return 0.68f * remaining * (0.35f + 0.65f * remaining);
    }

    static float comboScale(int combo) {
        return 1.0f + SIZE_LOG_GROWTH * comboGrowth(combo);
    }

    static float waveAmplitude(int combo) {
        return BASE_WAVE_AMPLITUDE * (1.0f + WAVE_LOG_GROWTH * comboGrowth(combo));
    }

    static float waveOffsetY(long nowMs, int digitIndex, int combo) {
        return waveAmplitude(combo) * (float) Math.sin(nowMs * 0.012 - digitIndex * 0.9);
    }

    static float disappearProgress(long ageMs) {
        return Math.clamp((float) ageMs / DISAPPEAR_LIFETIME_MS, 0.0f, 1.0f);
    }

    static float disappearOffsetX(long ageMs, int branch) {
        float progress = disappearProgress(ageMs);
        float distance = 27.0f * (1.0f - (1.0f - progress) * (1.0f - progress));
        float angle = disappearAngle(progress, branch);
        return (float) Math.cos(angle) * distance;
    }

    static float disappearOffsetY(long ageMs, int branch) {
        float progress = disappearProgress(ageMs);
        float distance = 27.0f * (1.0f - (1.0f - progress) * (1.0f - progress));
        float angle = disappearAngle(progress, branch);
        return (float) Math.sin(angle) * distance;
    }

    static float disappearAlpha(long ageMs) {
        float remaining = 1.0f - disappearProgress(ageMs);
        return 0.72f * remaining * remaining;
    }

    static float disappearScale(long ageMs) {
        return 1.0f - 0.38f * disappearProgress(ageMs);
    }

    private static float disappearAngle(float progress, int branch) {
        float radialAngle = (float) (Math.PI * 2.0 * branch / 6.0);
        return radialAngle + 1.05f * progress;
    }

    private static float comboGrowth(int combo) {
        float excess = Math.max(0, combo - COMBO_GROWTH_THRESHOLD);
        return (float) Math.log1p(excess / COMBO_GROWTH_RANGE);
    }

    record Frame(float scale, float bounceY) {
    }

    record Rgb(float red, float green, float blue) {
    }
}

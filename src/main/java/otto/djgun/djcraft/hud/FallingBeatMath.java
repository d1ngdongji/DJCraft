package otto.djgun.djcraft.hud;

import otto.djgun.djcraft.data.BeatPostJudgmentBehavior;

/** Pure positioning and animation math for the falling chart renderer. */
public final class FallingBeatMath {
    public static final long FREEZE_HOLD_MS = 120L;
    public static final long FREEZE_FADE_MS = 180L;
    public static final long DISSIPATE_MS = 220L;
    public static final long BOUNCE_MS = 320L;
    public static final float BOUNCE_HEIGHT = 28.0f;
    public static final long IMPACT_WAVE_MS = 320L;
    public static final long IMPACT_FLASH_MS = 90L;

    private FallingBeatMath() {
    }

    public static float markerTopY(long currentTimeMs, long beatTimeMs, int spawnAdvanceMs,
            float judgmentLineY, float markerHeight) {
        int sanitizedAdvance = Math.max(1, spawnAdvanceMs);
        long spawnTimeMs = beatTimeMs - sanitizedAdvance;
        double progress = (double) (currentTimeMs - spawnTimeMs) / sanitizedAdvance;
        float centerY = (float) (progress * judgmentLineY);
        return centerY - markerHeight * 0.5f;
    }

    public static float fillProgress(long currentTimeMs, long cycleStartMs, long targetBeatMs) {
        if (targetBeatMs <= cycleStartMs) {
            return currentTimeMs >= targetBeatMs ? 1.0f : 0.0f;
        }
        return clamp01((float) (currentTimeMs - cycleStartMs) / (targetBeatMs - cycleStartMs));
    }

    public static float rotationDegrees(long elapsedSinceSpawnMs, float rotationRpm) {
        if (!Float.isFinite(rotationRpm) || rotationRpm == 0.0f) {
            return 0.0f;
        }
        double degrees = elapsedSinceSpawnMs * (double) rotationRpm * 360.0 / 60_000.0;
        double wrapped = degrees % 360.0;
        return (float) (wrapped < 0.0 ? wrapped + 360.0 : wrapped);
    }

    public static EffectFrame effectFrame(BeatPostJudgmentBehavior behavior, long elapsedMs) {
        long age = Math.max(0L, elapsedMs);
        return switch (behavior) {
            case NONE -> new EffectFrame(0.0f, 1.0f, 1.0f, false);
            case FREEZE_DISSIPATE -> freezeDissipate(age);
            case DISSIPATE -> {
                float progress = clamp01((float) age / DISSIPATE_MS);
                yield new EffectFrame(0.0f, 1.0f + 0.65f * progress, 1.0f - progress,
                        age < DISSIPATE_MS);
            }
            case BOUNCE -> {
                float progress = clamp01((float) age / BOUNCE_MS);
                float arc = (float) Math.sin(Math.PI * progress) * (1.0f - 0.35f * progress);
                yield new EffectFrame(-BOUNCE_HEIGHT * arc, 1.0f + 0.12f * arc,
                        1.0f - progress, age < BOUNCE_MS);
            }
        };
    }

    public static long effectDurationMs(BeatPostJudgmentBehavior behavior) {
        return switch (behavior) {
            case NONE -> 0L;
            case FREEZE_DISSIPATE -> FREEZE_HOLD_MS + FREEZE_FADE_MS;
            case DISSIPATE -> DISSIPATE_MS;
            case BOUNCE -> BOUNCE_MS;
        };
    }

    public static ImpactWaveFrame impactWaveFrame(long elapsedMs) {
        if (elapsedMs < 0L || elapsedMs >= IMPACT_WAVE_MS) {
            return new ImpactWaveFrame(0.0f, 0.0f, 0.0f, 0.0f, false);
        }
        float progress = clamp01((float) elapsedMs / IMPACT_WAVE_MS);
        float expansion = 1.0f - (1.0f - progress) * (1.0f - progress);
        float fade = 1.0f - progress;
        float flashAlpha = 1.0f - clamp01((float) elapsedMs / IMPACT_FLASH_MS);
        return new ImpactWaveFrame(
                10.0f + 58.0f * expansion,
                0.52f * fade,
                fade,
                flashAlpha,
                true);
    }

    private static EffectFrame freezeDissipate(long age) {
        if (age < FREEZE_HOLD_MS) {
            return new EffectFrame(0.0f, 1.0f, 1.0f, true);
        }
        long fadeAge = age - FREEZE_HOLD_MS;
        float progress = clamp01((float) fadeAge / FREEZE_FADE_MS);
        return new EffectFrame(0.0f, 1.0f + 0.35f * progress, 1.0f - progress,
                fadeAge < FREEZE_FADE_MS);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public record EffectFrame(float offsetY, float scale, float alpha, boolean visible) {
    }

    public record ImpactWaveFrame(float radius, float glowAlpha, float coreAlpha,
            float flashAlpha, boolean visible) {
    }
}

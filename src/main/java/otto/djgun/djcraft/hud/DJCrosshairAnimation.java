package otto.djgun.djcraft.hud;

/** Pure timing curve for the shared center-crosshair line recoil. */
final class DJCrosshairAnimation {
    static final long DURATION_MS = 250L;
    static final float MAX_OFFSET = 6.0f;

    private DJCrosshairAnimation() {
    }

    static float lineOffset(long elapsedMs) {
        if (elapsedMs < 0L || elapsedMs >= DURATION_MS) {
            return 0.0f;
        }
        float progress = (float) elapsedMs / DURATION_MS;
        float remaining = 1.0f - progress;
        return MAX_OFFSET * remaining * remaining;
    }
}

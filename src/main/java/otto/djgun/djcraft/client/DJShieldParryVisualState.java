package otto.djgun.djcraft.client;

import net.minecraft.world.InteractionHand;

public final class DJShieldParryVisualState {
    private static long sessionId = -1L;
    private static InteractionHand hand;
    private static long expiresAtMs = -1L;

    private DJShieldParryVisualState() {
    }

    public static void activate(long activeSessionId, InteractionHand activeHand, long expiryMs) {
        sessionId = activeSessionId;
        hand = activeHand;
        expiresAtMs = expiryMs;
    }

    public static boolean isActive(long activeSessionId, InteractionHand renderedHand, long currentTimeMs) {
        if (sessionId != activeSessionId || hand != renderedHand || currentTimeMs >= expiresAtMs) {
            if (sessionId == activeSessionId && currentTimeMs >= expiresAtMs) {
                reset();
            }
            return false;
        }
        return true;
    }

    public static void reset() {
        sessionId = -1L;
        hand = null;
        expiresAtMs = -1L;
    }
}

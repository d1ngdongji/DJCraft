package otto.djgun.djcraft.combat.client;

/** Client-local visual state derived from server-authorized deferred-damage prompts. */
public final class DJDeferredDamageIndicatorState {
    private static long sessionId = Long.MIN_VALUE;
    private static boolean pending;

    private DJDeferredDamageIndicatorState() {
    }

    public static void update(long updatedSessionId, boolean hasPendingDamage) {
        sessionId = updatedSessionId;
        pending = hasPendingDamage;
    }

    public static boolean hasPending(long activeSessionId) {
        return pending && sessionId == activeSessionId;
    }

    static void reset() {
        sessionId = Long.MIN_VALUE;
        pending = false;
    }
}

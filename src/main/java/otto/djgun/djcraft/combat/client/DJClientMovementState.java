package otto.djgun.djcraft.combat.client;

/** Pure client snapshot and short-term prediction for DJ movement abilities. */
public final class DJClientMovementState {
    private final long sessionId;
    private int dashCooldownTicks;
    private int consecutiveDashes;
    private int remainingAirJumps;

    public DJClientMovementState(long sessionId) {
        this.sessionId = sessionId;
    }

    public boolean apply(long stateSessionId, int cooldownTicks, int authoritativeConsecutiveDashes,
            int airJumpsRemaining) {
        if (stateSessionId != sessionId) {
            return false;
        }
        int authoritativeCooldown = Math.max(0, cooldownTicks);
        if (authoritativeCooldown == 0 || dashCooldownTicks == 0) {
            dashCooldownTicks = authoritativeCooldown;
        } else {
            dashCooldownTicks = Math.min(dashCooldownTicks, authoritativeCooldown);
        }
        consecutiveDashes = Math.max(0, authoritativeConsecutiveDashes);
        remainingAirJumps = Math.max(0, airJumpsRemaining);
        return true;
    }

    public void tick() {
        if (dashCooldownTicks > 0) {
            dashCooldownTicks--;
        }
    }

    public void predictDash(int maxConsecutiveDashes, int cooldownTicks) {
        consecutiveDashes++;
        if (consecutiveDashes >= Math.max(1, maxConsecutiveDashes)) {
            consecutiveDashes = 0;
            dashCooldownTicks = Math.max(dashCooldownTicks, Math.max(0, cooldownTicks));
        }
    }

    public void predictAirJumpUsed() {
        remainingAirJumps = Math.max(0, remainingAirJumps - 1);
    }

    public void reset() {
        dashCooldownTicks = 0;
        consecutiveDashes = 0;
        remainingAirJumps = 0;
    }

    public int getDashCooldownTicks() {
        return dashCooldownTicks;
    }

    public int getConsecutiveDashes() {
        return consecutiveDashes;
    }

    public int getRemainingAirJumps() {
        return remainingAirJumps;
    }
}

package otto.djgun.djcraft.session;

/** Pure server-side cooldown and per-airborne-period movement state. */
public final class DJMovementAbilityState {
    private int maxAirJumps;
    private int airJumpsUsed;
    private long dashCooldownEndTick;
    private int consecutiveDashes;

    public DJMovementAbilityState(int maxAirJumps) {
        this.maxAirJumps = Math.max(0, maxAirJumps);
    }

    public boolean setMaxAirJumps(int maxAirJumps) {
        int previousRemaining = remainingAirJumps();
        this.maxAirJumps = Math.max(0, maxAirJumps);
        return previousRemaining != remainingAirJumps();
    }

    public boolean canDash(long gameTime) {
        return gameTime >= dashCooldownEndTick;
    }

    public void startDashCooldown(long gameTime, int durationTicks) {
        dashCooldownEndTick = Math.max(dashCooldownEndTick, gameTime + Math.max(0, durationTicks));
    }

    public int dashCooldownTicks(long gameTime) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, dashCooldownEndTick - gameTime));
    }

    public boolean recordDashAndMaybeStartCooldown(long gameTime, int maxConsecutiveDashes,
            int cooldownDurationTicks) {
        consecutiveDashes++;
        if (consecutiveDashes < Math.max(1, maxConsecutiveDashes)) {
            return false;
        }
        consecutiveDashes = 0;
        startDashCooldown(gameTime, cooldownDurationTicks);
        return true;
    }

    public int consecutiveDashes() {
        return consecutiveDashes;
    }

    public boolean tryUseAirJump() {
        if (airJumpsUsed >= maxAirJumps) {
            return false;
        }
        airJumpsUsed++;
        return true;
    }

    public boolean resetAirJumpsIfGrounded(boolean grounded) {
        if (!grounded || airJumpsUsed == 0) {
            return false;
        }
        airJumpsUsed = 0;
        return true;
    }

    public int remainingAirJumps() {
        return Math.max(0, maxAirJumps - airJumpsUsed);
    }
}

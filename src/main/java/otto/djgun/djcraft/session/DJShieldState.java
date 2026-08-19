package otto.djgun.djcraft.session;

import java.util.Optional;

/** Server-authoritative pending shield authorization and active hold state. */
public final class DJShieldState<H, I> {
    private PendingStart<H, I> pendingStart;
    private ActiveShield<H, I> activeShield;

    public void authorizeStart(H hand, I item, long actionSequence, long judgedAtMs, long parryExpiresAtMs,
            boolean parryEnabled, double startVirtualBeat, long expiresAtTick) {
        pendingStart = new PendingStart<>(hand, item, actionSequence, judgedAtMs, parryExpiresAtMs,
                parryEnabled, startVirtualBeat, expiresAtTick);
    }

    public Optional<PendingStart<H, I>> takeStartAuthorization(H hand, I item, long gameTime) {
        PendingStart<H, I> pending = pendingStart;
        pendingStart = null;
        if (pending == null || gameTime > pending.expiresAtTick()
                || !pending.hand().equals(hand) || pending.item() != item) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public void activate(PendingStart<H, I> pending, double sustainIntervalBeats) {
        activeShield = new ActiveShield<>(pending.hand(), pending.item(), pending.actionSequence(),
                pending.parryExpiresAtMs(),
                pending.parryEnabled(), false, pending.startVirtualBeat() + sustainIntervalBeats);
    }

    public boolean isActiveFor(H hand, I item) {
        return activeShield != null && activeShield.hand().equals(hand) && activeShield.item() == item;
    }

    public boolean isSustainChargeDue(double currentVirtualBeat) {
        return activeShield != null && currentVirtualBeat + 1.0E-9 >= activeShield.nextChargeVirtualBeat();
    }

    public void recordSustainCharge(double sustainIntervalBeats) {
        if (activeShield != null) {
            activeShield = activeShield.withNextCharge(activeShield.nextChargeVirtualBeat() + sustainIntervalBeats);
        }
    }

    public Optional<ParryResult> tryParry(long currentTimeMs) {
        if (activeShield == null || !activeShield.parryAvailable()
                || currentTimeMs >= activeShield.parryExpiresAtMs()) {
            return Optional.empty();
        }
        boolean rewardEnergy = !activeShield.hasParried();
        activeShield = activeShield.withHasParried(true);
        return Optional.of(new ParryResult(activeShield.actionSequence(), rewardEnergy));
    }

    public boolean hasActiveShield() {
        return activeShield != null;
    }

    public long activeParryExpiresAtMs() {
        return activeShield != null && activeShield.parryAvailable()
                ? activeShield.parryExpiresAtMs() : -1L;
    }

    public Optional<ShieldEnd<I>> finishActiveShield() {
        if (activeShield == null) {
            return Optional.empty();
        }
        ShieldEnd<I> result = new ShieldEnd<>(activeShield.item(), !activeShield.hasParried());
        activeShield = null;
        return Optional.of(result);
    }

    public void clear() {
        pendingStart = null;
        activeShield = null;
    }

    public record PendingStart<H, I>(H hand, I item, long actionSequence, long judgedAtMs, long parryExpiresAtMs,
            boolean parryEnabled, double startVirtualBeat, long expiresAtTick) {
    }

    public record ParryResult(long actionSequence, boolean rewardEnergy) {
    }

    public record ShieldEnd<I>(I item, boolean applyMissedParryCooldown) {
    }

    private record ActiveShield<H, I>(H hand, I item, long actionSequence, long parryExpiresAtMs,
            boolean parryAvailable, boolean hasParried, double nextChargeVirtualBeat) {
        private ActiveShield<H, I> withHasParried(boolean value) {
            return new ActiveShield<>(hand, item, actionSequence, parryExpiresAtMs,
                    parryAvailable, value, nextChargeVirtualBeat);
        }

        private ActiveShield<H, I> withNextCharge(double virtualBeat) {
            return new ActiveShield<>(hand, item, actionSequence, parryExpiresAtMs,
                    parryAvailable, hasParried, virtualBeat);
        }
    }
}

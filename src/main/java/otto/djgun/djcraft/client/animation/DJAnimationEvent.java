package otto.djgun.djcraft.client.animation;

import otto.djgun.djcraft.sound.DJActionOutcome;

/** An immutable visual event created only after the corresponding gameplay event occurred. */
public record DJAnimationEvent(
        long sequence,
        long timelineGeneration,
        DJAnimationHand hand,
        Object renderIdentity,
        String itemIdentity,
        DJAnimationSemantic semantic,
        long sessionTimeMs,
        double virtualBeat,
        double durationBeats,
        DJActionOutcome outcome) {

    public DJAnimationEvent {
        if (sequence <= 0 || timelineGeneration <= 0 || hand == null || renderIdentity == null
                || itemIdentity == null || itemIdentity.isBlank() || semantic == null || sessionTimeMs < 0
                || !Double.isFinite(virtualBeat) || !Double.isFinite(durationBeats)
                || durationBeats < 0.0 || outcome == null) {
            throw new IllegalArgumentException("Invalid DJ animation event");
        }
    }

    /** Compatibility constructor for the original built-in-only event API. */
    @Deprecated(forRemoval = false)
    public DJAnimationEvent(long sequence, long timelineGeneration, DJAnimationHand hand,
            Object renderIdentity, String itemIdentity, Kind kind, long sessionTimeMs,
            double virtualBeat, double durationBeats, DJActionOutcome outcome) {
        this(sequence, timelineGeneration, hand, renderIdentity, itemIdentity, kind.semantic(),
                sessionTimeMs, virtualBeat, durationBeats, outcome);
    }

    /** Returns the legacy built-in kind, or {@code null} for an addon semantic. */
    @Deprecated(forRemoval = false)
    public Kind kind() {
        return Kind.from(semantic);
    }

    public boolean successful() {
        return outcome.successful();
    }

    /** @deprecated Use {@link DJAnimationSemantic} so addon-defined semantics are representable. */
    @Deprecated(forRemoval = false)
    public enum Kind {
        MELEE_STRIKE(DJAnimationSemantic.MELEE_STRIKE),
        MELEE_THRUST(DJAnimationSemantic.MELEE_THRUST),
        MELEE_SWEEP(DJAnimationSemantic.MELEE_SWEEP),
        MELEE_CRITICAL(DJAnimationSemantic.MELEE_CRITICAL),
        TRIGGER_IMPACT(DJAnimationSemantic.TRIGGER_IMPACT),
        CHARGE_START(DJAnimationSemantic.CHARGE_START),
        CHARGE_RELEASE(DJAnimationSemantic.CHARGE_RELEASE),
        UNEQUIP_START(DJAnimationSemantic.UNEQUIP_START),
        EQUIP_START(DJAnimationSemantic.EQUIP_START),
        RELOAD_START(DJAnimationSemantic.RELOAD_START),
        INSPECT_START(DJAnimationSemantic.INSPECT_START),
        USE(DJAnimationSemantic.USE),
        USE_START(DJAnimationSemantic.USE_START),
        USE_RELEASE(DJAnimationSemantic.USE_RELEASE),
        PARRY(DJAnimationSemantic.PARRY),
        READY(DJAnimationSemantic.READY),
        CANCEL(DJAnimationSemantic.CANCEL);

        private final DJAnimationSemantic semantic;

        Kind(DJAnimationSemantic semantic) {
            this.semantic = semantic;
        }

        public DJAnimationSemantic semantic() {
            return semantic;
        }

        private static Kind from(DJAnimationSemantic semantic) {
            for (Kind kind : values()) {
                if (kind.semantic == semantic) {
                    return kind;
                }
            }
            return null;
        }
    }
}

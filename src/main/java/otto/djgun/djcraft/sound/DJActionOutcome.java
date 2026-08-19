package otto.djgun.djcraft.sound;

import otto.djgun.djcraft.combat.HitResult;

public record DJActionOutcome(BeatOutcome beat, TargetOutcome target) {
    public static final DJActionOutcome NOT_JUDGED =
            new DJActionOutcome(BeatOutcome.NOT_APPLICABLE, TargetOutcome.NOT_APPLICABLE);

    public DJActionOutcome {
        if (beat == null || target == null) {
            throw new IllegalArgumentException("Action outcomes must not be null");
        }
    }

    public static DJActionOutcome judged(HitResult result, boolean canHitTarget) {
        return new DJActionOutcome(result.isHit() ? BeatOutcome.HIT : BeatOutcome.MISS,
                result.isHit() && canHitTarget ? TargetOutcome.UNKNOWN : TargetOutcome.NOT_APPLICABLE);
    }

    public boolean successful() {
        return beat != BeatOutcome.MISS;
    }
}

package otto.djgun.djcraft.combat;

import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.data.TrackPack;

public final class DJJudgmentProofEvaluator {
    private DJJudgmentProofEvaluator() {
    }

    public static HitResult evaluate(boolean claimedHit, long clientTimeMs, int claimedBeatIndex,
            TrackPack trackPack) {
        HitResult evaluated = BeatJudgmentEvaluator.evaluate(clientTimeMs, trackPack,
                DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
        return resolve(claimedHit, clientTimeMs, claimedBeatIndex, evaluated);
    }

    public static HitResult resolve(boolean claimedHit, long clientTimeMs, int claimedBeatIndex,
            HitResult evaluated) {
        if (claimedHit && evaluated.isHit() && evaluated.beatIndex() == claimedBeatIndex) {
            return evaluated;
        }
        return HitResult.miss(evaluated.beatData(), evaluated.beatEvent(), evaluated.beatIndex(), clientTimeMs);
    }
}

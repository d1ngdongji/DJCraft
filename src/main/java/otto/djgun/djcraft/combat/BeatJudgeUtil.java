package otto.djgun.djcraft.combat;

import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.session.DJSessionClient;

/** Session adapters for the pure {@link BeatJudgmentEvaluator}. */
public final class BeatJudgeUtil {

    private BeatJudgeUtil() {
    }

    public static HitResult judge(DJSession session) {
        if (session == null || !session.isPlaying()) {
            return HitResult.miss(0L);
        }
        long currentTimeMs = session.getCurrentTimeMs();
        return BeatJudgmentEvaluator.evaluate(currentTimeMs, session.getTrackPack(),
                DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
    }

    public static HitResult judge(DJSessionClient session) {
        if (session == null || !session.isPlaying()) {
            return HitResult.miss(0L);
        }
        long currentTimeMs = session.getCurrentTimeMs();
        return BeatJudgmentEvaluator.evaluate(currentTimeMs, session.getTrackPack(),
                DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
    }
}

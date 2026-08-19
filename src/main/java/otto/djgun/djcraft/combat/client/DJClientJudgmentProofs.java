package otto.djgun.djcraft.combat.client;

import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.network.packet.DJJudgmentProof;
import otto.djgun.djcraft.session.DJSessionClient;

public final class DJClientJudgmentProofs {
    private DJClientJudgmentProofs() {
    }

    public static DJJudgmentProof create(DJSessionClient session, HitResult result) {
        long sequence = session.nextActionSequence();
        session.recordAttackJudgment(sequence, result.isHit());
        return new DJJudgmentProof(session.getSessionId(), sequence, result.isHit(),
                result.judgedAtMs(), result.beatIndex());
    }

    public static DJJudgmentProof createNonOffensive(DJSessionClient session, HitResult result) {
        long sequence = session.nextActionSequence();
        return new DJJudgmentProof(session.getSessionId(), sequence, result.isHit(),
                result.judgedAtMs(), result.beatIndex());
    }

    public static DJJudgmentProof createUnjudged(DJSessionClient session) {
        return new DJJudgmentProof(session.getSessionId(), session.nextActionSequence(), false,
                session.getCurrentTimeMs(), -1);
    }
}

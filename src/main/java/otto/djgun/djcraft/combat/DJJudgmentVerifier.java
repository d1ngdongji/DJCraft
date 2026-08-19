package otto.djgun.djcraft.combat;

import net.minecraft.server.level.ServerPlayer;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.network.packet.DJJudgmentProof;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

public final class DJJudgmentVerifier {

    private DJJudgmentVerifier() {
    }

    public record Verification(DJSession session, HitResult result, boolean stopAfterAction, boolean accepted) {
        public static Verification rejected() {
            return new Verification(null, HitResult.miss(0L), false, false);
        }
    }

    public static Verification verify(ServerPlayer player, DJJudgmentProof proof) {
        return verify(player, proof, DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
    }

    public static Verification verify(ServerPlayer player, DJJudgmentProof proof, boolean disabledByCanAttack) {
        DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
        if (session == null || !session.isPlaying() || session.getSessionId() != proof.sessionId()) {
            return Verification.rejected();
        }
        if (!session.acceptActionSequence(proof.actionSequence())) {
            DJCraft.LOGGER.warn("Ignored replayed/out-of-order DJ action {} from {}",
                    proof.actionSequence(), player.getName().getString());
            return new Verification(session, HitResult.miss(proof.clientTimeMs()), false, false);
        }

        HitResult evaluated = BeatJudgmentEvaluator.evaluate(
                proof.clientTimeMs(), session.getTrackPack(), disabledByCanAttack);
        HitResult result = DJJudgmentProofEvaluator.resolve(proof.hit(), proof.clientTimeMs(), proof.beatIndex(),
                evaluated);
        if (proof.hit() && !result.isHit()) {
            DJCraft.LOGGER.warn("DJ hit proof was not self-consistent for {} (claimedBeat={}, actualBeat={})",
                    player.getName().getString(), proof.beatIndex(), evaluated.beatIndex());
        }

        boolean stopAfterAction = auditClock(player, session, proof.clientTimeMs());
        return new Verification(session, result, stopAfterAction, true);
    }

    private static boolean auditClock(ServerPlayer player, DJSession session, long clientTimeMs) {
        int pingMs = Math.max(0, player.connection.latency());
        var audit = session.auditClientClock(clientTimeMs, pingMs);
        if (audit.anomalous()) {
            DJCraft.LOGGER.warn("DJ client clock differs by {}ms for {} (threshold={}ms, consecutive={}/5)",
                    audit.differenceMs(), player.getName().getString(), audit.thresholdMs(),
                    audit.consecutiveAnomalies());
        }
        return audit.stopSession();
    }
}

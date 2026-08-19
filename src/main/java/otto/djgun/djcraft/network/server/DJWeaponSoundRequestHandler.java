package otto.djgun.djcraft.network.server;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.combat.DJJudgmentProofEvaluator;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.network.packet.DJWeaponSoundIntentPayload;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;

public final class DJWeaponSoundRequestHandler {
    private static final DJWeaponSoundRateLimiter<UUID> RATE_LIMITER = new DJWeaponSoundRateLimiter<>(12.0, 2.0);

    private DJWeaponSoundRequestHandler() {
    }

    public static void handle(DJWeaponSoundIntentPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.soundSequence() <= 0
                || payload.semantic() == DJWeaponSoundSemantic.TARGET_HIT) {
            return;
        }
        var session = DJModeManager.getInstance().getSession(player).orElse(null);
        if (session == null || !session.isPlaying() || session.getSessionId() != payload.sessionId()
                || !DJWeaponSoundIdentityRegistry.playerOwnsProfile(player, payload.profileId())
                || !RATE_LIMITER.accept(player.getUUID(), player.level().getGameTime(), payload.soundSequence())) {
            return;
        }
        if (payload.beat() != BeatOutcome.NOT_APPLICABLE) {
            var evaluated = DJJudgmentProofEvaluator.evaluate(payload.beat() == BeatOutcome.HIT,
                    payload.judgedAtMs(), payload.beatIndex(), session.getTrackPack());
            if (evaluated.isHit() != (payload.beat() == BeatOutcome.HIT)) {
                return;
            }
        }
        var broadcast = new DJWeaponSoundBroadcastPayload(payload.soundSequence(), player.getUUID(),
                payload.actionSequence(), payload.hand(),
                payload.semantic(), payload.profileId(), payload.beat(), payload.target(),
                player.getX(), player.getY(), player.getZ(), payload.seed());
        PacketDistributor.sendToPlayersNear(player.serverLevel(), player, player.getX(), player.getY(), player.getZ(),
                64.0, broadcast);
    }

    public static void cleanupPlayer(UUID playerId) {
        RATE_LIMITER.remove(playerId);
    }

    public static void clear() {
        RATE_LIMITER.clear();
    }
}

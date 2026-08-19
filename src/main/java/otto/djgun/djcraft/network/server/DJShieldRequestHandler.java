package otto.djgun.djcraft.network.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJJudgmentVerifier;
import otto.djgun.djcraft.network.packet.DJShieldUsePayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

public final class DJShieldRequestHandler {
    private DJShieldRequestHandler() {
    }

    public static void handle(DJShieldUsePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var stack = payload.source().resolveAtActionStart(player, payload.hand());
        var behavior = DJItemBehaviorManager.resolveDefinition(stack);
        var verification = DJJudgmentVerifier.verify(
                player, payload.proof(), behavior.disabledByCanAttack());
        if (verification.session() == null || !verification.accepted()) {
            return;
        }

        if (DJItemBehaviorManager.is(stack, DJItemBehavior.SHIELD) && !player.isUsingItem()
                && !player.getCooldowns().isOnCooldown(stack.getItem())) {
            verification.session().authorizeShieldStart(payload.hand(), stack.getItem(), verification.result(),
                    payload.proof().actionSequence(), player.level().getGameTime());
        }

        if (verification.stopAfterAction()) {
            player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
            DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
            DJCraft.LOGGER.warn("Stopped DJ session for {} after five clock anomalies",
                    player.getName().getString());
        }
    }
}

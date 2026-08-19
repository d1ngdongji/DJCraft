package otto.djgun.djcraft.combat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.combat.DJShieldRules;
import otto.djgun.djcraft.network.packet.DJShieldUsePayload;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.combat.DJItemBehavior;

/** Handles client-side shield judgment while the server remains authoritative. */
@OnlyIn(Dist.CLIENT)
public final class ClientShieldHandler {
    private ClientShieldHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        var context = DJClientEventContext.resolve(event, DJItemBehavior.SHIELD);
        if (context.isEmpty()) {
            return;
        }
        var resolved = context.get();
        if (resolved.player().isUsingItem()) {
            return;
        }
        if (resolved.player().getCooldowns().isOnCooldown(resolved.stack().getItem())
                || resolved.session().getEnergy() < DJShieldRules.START_ENERGY_COST) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        var behavior = otto.djgun.djcraft.combat.DJItemBehaviorManager.resolveDefinition(resolved.stack());
        var result = BeatJudgeFacade.judgeAndNotify(resolved.session(), behavior);
        var proof = DJClientJudgmentProofs.createNonOffensive(resolved.session(), result);
        PacketDistributor.sendToServer(new DJShieldUsePayload(proof, resolved.hand(),
                resolved.source()));
        DJAnimationRuntime.getInstance().emit(
                DJAnimationEvent.Kind.USE_START, resolved.hand(), resolved.stack(), resolved.session(), 1.0,
                DJActionOutcome.judged(result, false), 0L, result.judgedAtMs(), result.beatIndex());
    }
}

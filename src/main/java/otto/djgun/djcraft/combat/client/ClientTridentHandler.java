package otto.djgun.djcraft.combat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.network.packet.DJTridentFirePayload;

@OnlyIn(Dist.CLIENT)
public final class ClientTridentHandler {
    private ClientTridentHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        var context = DJClientEventContext.resolve(event, DJItemBehavior.TRIDENT);
        if (context.isEmpty()) {
            return;
        }
        var resolved = context.get();
        event.setCanceled(true);
        event.setSwingHand(false);
        if (resolved.player().getCooldowns().isOnCooldown(resolved.stack().getItem())
                || resolved.session().getEnergy() < DJItemCooldownManager.getUseEnergyCost(resolved.stack())
                || resolved.stack().getDamageValue() >= resolved.stack().getMaxDamage() - 1) {
            return;
        }

        var result = BeatJudgeFacade.judgeAttackAndNotify(resolved.session(), resolved.stack());
        var proof = DJClientJudgmentProofs.create(resolved.session(), result);
        PacketDistributor.sendToServer(new DJTridentFirePayload(proof, resolved.hand(),
                resolved.source()));
        int cooldown = DJItemCooldownManager.getUseBeatCooldown(resolved.stack());
        DJTriggerWeaponHelper.applyBeats(resolved.player(), resolved.stack(), resolved.hand(), resolved.session(),
                Math.max(0.0, cooldown - 0.4), cooldown, result, true);
    }
}

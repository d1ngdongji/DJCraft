package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.network.packet.DJEatingPayload;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Client timing capture for non-offensive utility actions. */
@OnlyIn(Dist.CLIENT)
public final class DJUtilityActionClientHandler {
    private DJUtilityActionClientHandler() {
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player
                || event.getItem().getFoodProperties(player) == null) {
            return;
        }
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null) {
            return;
        }

        var result = BeatJudgeFacade.judgeAndNotify(session, DJItemBehaviorRegistry.EATING);
        var proof = DJClientJudgmentProofs.createNonOffensive(session, result);
        InteractionHand hand = event.getItem() == player.getOffhandItem()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        PacketDistributor.sendToServer(new DJEatingPayload(proof, hand));
    }
}

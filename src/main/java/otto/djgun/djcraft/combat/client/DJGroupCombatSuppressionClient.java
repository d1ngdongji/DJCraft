package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import otto.djgun.djcraft.client.DJNetworkGroupClient;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

/** Prevents recovery gaps from falling through to vanilla combat. */
@OnlyIn(Dist.CLIENT)
public final class DJGroupCombatSuppressionClient {
    private DJGroupCombatSuppressionClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!DJNetworkGroupClient.getInstance().shouldSuppressVanillaCombat()) {
            return;
        }
        boolean suppress = event.isAttack();
        if (event.isUseItem()) {
            var player = Minecraft.getInstance().player;
            var hand = event.getHand();
            suppress = player != null && hand != null
                    && DJItemBehaviorManager.resolve(player.getItemInHand(hand)) != DJItemBehavior.NONE;
        }
        if (suppress) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}

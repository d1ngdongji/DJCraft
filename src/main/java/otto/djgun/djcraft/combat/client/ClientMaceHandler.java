package otto.djgun.djcraft.combat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.network.packet.DJMaceThrowPayload;

@OnlyIn(Dist.CLIENT)
public final class ClientMaceHandler {
    private ClientMaceHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        var context = DJClientEventContext.resolve(event, DJItemBehavior.MACE);
        if (context.isEmpty()) {
            return;
        }
        var resolved = context.get();
        DJTriggerWeaponHelper.handlePress(event, resolved.player(), resolved.stack(), resolved.hand(),
                resolved.session(), result -> PacketDistributor.sendToServer(new DJMaceThrowPayload(
                        DJClientJudgmentProofs.create(resolved.session(), result), resolved.hand(),
                        resolved.source())));
    }
}

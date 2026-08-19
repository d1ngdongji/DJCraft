package otto.djgun.djcraft.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.DJActionSourceHistory;

/** Captures the authoritative slot immediately before the server applies a hotbar switch. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerSelectionMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void djcraft$capturePreviousSelection(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        DJActionSourceHistory.recordSelectionChange(player, packet.getSlot());
    }
}

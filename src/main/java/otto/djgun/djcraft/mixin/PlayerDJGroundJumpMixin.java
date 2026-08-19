package otto.djgun.djcraft.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.session.DJModeManager;

/** Replaces vanilla server-side ground jumps with DJCraft's judged jump request. */
@Mixin(Player.class)
public class PlayerDJGroundJumpMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void djCancelVanillaServerJump(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer && DJModeManager.getInstance().isInDJMode(player)) {
            ci.cancel();
        }
    }
}

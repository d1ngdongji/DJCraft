package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Replaces vanilla client-side ground jumps with the predicted DJCraft jump. */
@Mixin(Player.class)
public class PlayerDJGroundJumpClientMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void djCancelVanillaClientJump(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.level().isClientSide()
                && DJModeManagerClient.getInstance().getActiveSession().isPresent()) {
            ci.cancel();
        }
    }
}

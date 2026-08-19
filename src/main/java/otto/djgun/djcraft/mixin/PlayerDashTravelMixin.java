package otto.djgun.djcraft.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.DJDashMomentumController;

@Mixin(Player.class)
public class PlayerDashTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void djLockServerDashMomentum(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Vec3 momentum = DJDashMomentumController.consumeForTravel(serverPlayer);
        if (momentum == null) {
            return;
        }
        serverPlayer.setDeltaMovement(momentum);
        serverPlayer.move(MoverType.SELF, momentum);
        serverPlayer.setDeltaMovement(momentum);
        serverPlayer.resetFallDistance();
        serverPlayer.hasImpulse = true;
        serverPlayer.hurtMarked = true;
        ci.cancel();
    }
}

package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.client.DJClientDashMomentumState;

@Mixin(Player.class)
public class PlayerDashTravelClientMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void djLockClientDashMomentum(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide()) {
            return;
        }
        Vec3 momentum = DJClientDashMomentumState.consumeForTravel(player);
        if (momentum == null) {
            return;
        }
        player.setDeltaMovement(momentum);
        player.move(MoverType.SELF, momentum);
        player.setDeltaMovement(momentum);
        player.resetFallDistance();
        player.hasImpulse = true;
        ci.cancel();
    }
}

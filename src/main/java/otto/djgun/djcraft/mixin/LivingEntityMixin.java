package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJCombatHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes vanilla's five-tick shield startup while a server DJ session is active. */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void djInstantBlocking(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Player player && !player.level().isClientSide()) {
            DJModeManager.getInstance().getSession(player)
                    .filter(session -> session.isPlaying() && entity.isUsingItem()
                            && !entity.getUseItem().isEmpty()
                            && DJItemBehaviorManager.is(entity.getUseItem(), DJItemBehavior.SHIELD)
                            && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK)
                    .ifPresent(session -> cir.setReturnValue(true));
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void djEndProjectileFire(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.level().isClientSide() && entity instanceof Player player) {
            DJCombatHandler.endProjectileFire(player.getUUID());
        }
    }
}

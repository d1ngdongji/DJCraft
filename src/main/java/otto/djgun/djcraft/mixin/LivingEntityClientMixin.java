package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.DJShieldParryVisualState;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.sound.DJActionOutcome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

@Mixin(LivingEntity.class)
public class LivingEntityClientMixin {
    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void djInstantBlockingClient(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Player player && player.level().isClientSide()) {
            DJModeManagerClient.getInstance().getActiveSession()
                    .filter(session -> entity.isUsingItem() && !entity.getUseItem().isEmpty()
                            && DJItemBehaviorManager.is(entity.getUseItem(), DJItemBehavior.SHIELD)
                            && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK)
                    .ifPresent(session -> cir.setReturnValue(true));
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void djShieldReleaseAnimation(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player) || !player.level().isClientSide()
                || !player.isUsingItem()
                || !DJItemBehaviorManager.is(player.getUseItem(), DJItemBehavior.SHIELD)) {
            return;
        }
        DJModeManagerClient.getInstance().getActiveSession().ifPresent(session ->
                DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.USE_RELEASE,
                        player.getUsedItemHand(), player.getUseItem(), session, 0.35,
                        DJActionOutcome.NOT_JUDGED));
        DJShieldParryVisualState.reset();
    }
}

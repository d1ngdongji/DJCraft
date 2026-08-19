package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.DJAreaMeleeCombatService;

@Mixin(MaceItem.class)
public class MaceItemCommonMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void djcraft$limitSmashEffectsToPrimaryTarget(ItemStack stack, LivingEntity target,
            LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (DJAreaMeleeCombatService.isAreaAttackActive(attacker)
                && !DJAreaMeleeCombatService.isPrimaryTargetEffect()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "postHurtEnemy", at = @At("HEAD"), cancellable = true)
    private void djcraft$deferAreaDurabilityAndFallReset(ItemStack stack, LivingEntity target,
            LivingEntity attacker, CallbackInfo ci) {
        if (DJAreaMeleeCombatService.isAreaAttackActive(attacker)) {
            ci.cancel();
        }
    }

}

package otto.djgun.djcraft.mixin;

import java.util.function.Consumer;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.DJAreaMeleeCombatService;

/** Defers all per-target durability during a registered area action to its one final charge. */
@Mixin(ItemStack.class)
public abstract class ItemStackAreaDurabilityMixin {
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void djcraft$suppressPerTargetAreaDurability(int amount, ServerLevel level,
            @Nullable LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        if (entity != null
                && DJAreaMeleeCombatService.isAreaAttackStack(entity, (ItemStack) (Object) this)) {
            ci.cancel();
        }
    }
}

package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.session.DJModeManagerClient;

@OnlyIn(Dist.CLIENT)
@Mixin(TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void djcraft$blockVanillaDjRelease(ItemStack stack, Level level, LivingEntity entity,
            int timeCharged, CallbackInfo ci) {
        if (level.isClientSide() && DJModeManagerClient.getInstance().getActiveSession().isPresent()) {
            ci.cancel();
        }
    }
}

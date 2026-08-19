package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.session.DJModeManager;

@Mixin(TridentItem.class)
public class TridentItemCommonMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void djcraft$blockVanillaDjRelease(ItemStack stack, Level level, LivingEntity entity,
            int timeCharged, CallbackInfo ci) {
        if (!level.isClientSide() && entity instanceof Player player
                && DJModeManager.getInstance().getSession(player).filter(session -> session.isPlaying()).isPresent()) {
            ci.cancel();
        }
    }

}

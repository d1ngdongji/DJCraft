package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.DJCombatHandler;

/** Keeps one vanilla attack bound to the item slot captured by its DJ action context. */
@Mixin(Player.class)
public abstract class PlayerAttackContextMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void djcraft$bindActionSource(Entity target, CallbackInfo ci) {
        DJCombatHandler.beginVanillaAttack((Player) (Object) this);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void djcraft$restoreSelectedSlot(Entity target, CallbackInfo ci) {
        DJCombatHandler.endVanillaAttack((Player) (Object) this);
    }
}

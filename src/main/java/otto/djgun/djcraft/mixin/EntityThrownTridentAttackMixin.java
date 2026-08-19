package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;

@Mixin(Entity.class)
public abstract class EntityThrownTridentAttackMixin {
    @Inject(method = "skipAttackInteraction", at = @At("HEAD"), cancellable = true)
    private void djcraft$redirectReturningTrident(Entity attacker, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ThrownTrident trident
                && trident instanceof DJThrownTridentExtension extension
                && extension.djcraft$canBeRedirected()) {
            cir.setReturnValue(extension.djcraft$tryRedirect(attacker));
        }
    }
}

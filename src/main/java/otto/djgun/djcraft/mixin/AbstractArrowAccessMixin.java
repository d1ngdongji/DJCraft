package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.combat.access.AbstractArrowAccess;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowAccessMixin implements AbstractArrowAccess {
    @Shadow
    protected boolean inGround;

    @Shadow
    protected int inGroundTime;

    @Shadow
    public int shakeTime;

    @Shadow
    private void setPierceLevel(byte level) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract boolean canHitEntity(Entity entity);

    @Override
    @Unique
    public void djcraft$setPierceLevel(byte level) {
        setPierceLevel(level);
    }

    @Override
    @Unique
    public boolean djcraft$canHitEntity(Entity entity) {
        return canHitEntity(entity);
    }

    @Override
    @Unique
    public void djcraft$resetFlightState() {
        inGround = false;
        inGroundTime = 0;
        shakeTime = 0;
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void djcraft$makeReturningTridentAttackable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ThrownTrident trident
                && trident instanceof DJThrownTridentExtension extension
                && extension.djcraft$isDJTrident()) {
            cir.setReturnValue(extension.djcraft$canBeRedirected());
        }
    }
}

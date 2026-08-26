package otto.djgun.djcraft.mixin;

import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import otto.djgun.djcraft.combat.DJRelativeProjectileCollision;
import otto.djgun.djcraft.entity.DJThrownMace;

@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileCollisionMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult djcraft$useContinuousMaceCollision(Entity projectile, Predicate<Entity> filter) {
        if (projectile instanceof DJThrownMace && !projectile.level().isClientSide()) {
            return DJRelativeProjectileCollision.getHitResultOnMoveVector(projectile, filter);
        }
        return ProjectileUtil.getHitResultOnMoveVector(projectile, filter);
    }
}

package otto.djgun.djcraft.combat;

import java.util.function.Predicate;
import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Continuous entity collision shared by DJ projectiles with enlarged hit boxes. */
public final class DJRelativeProjectileCollision {
    private static final double MAX_TARGET_STEP = 4.0;
    private static final double MAX_TARGET_STEP_SQUARED = MAX_TARGET_STEP * MAX_TARGET_STEP;

    private DJRelativeProjectileCollision() {
    }

    public static HitResult getHitResultOnMoveVector(Entity projectile, Predicate<Entity> filter) {
        Vec3 start = projectile.position();
        Vec3 end = start.add(projectile.getDeltaMovement());
        HitResult blockHit = projectile.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = findFirstEntity(projectile.level(), projectile, start, entityEnd, filter);
        return entityHit == null ? blockHit : entityHit;
    }

    @Nullable
    public static EntityHitResult findFirstEntity(Level level, Entity projectile,
            Vec3 start, Vec3 end, Predicate<Entity> filter) {
        Vec3 projectileMovement = end.subtract(start);
        Vec3 boxOffset = start.subtract(projectile.position());
        AABB projectileStartBox = projectile.getBoundingBox().move(boxOffset);
        AABB searchBounds = projectileStartBox.expandTowards(projectileMovement)
                .inflate(MAX_TARGET_STEP);
        double stepFraction = stepFraction(projectile, start, projectileMovement);

        Entity bestEntity = null;
        double bestFraction = Double.POSITIVE_INFINITY;
        for (Entity target : level.getEntities(projectile, searchBounds, filter)) {
            TargetStep targetStep = targetStep(projectile, target);
            Vec3 targetMovement = targetStep.movement.scale(stepFraction);
            double fraction = DJProjectileCollisionMath.firstContactFraction(
                    box(projectileStartBox), vector(projectileMovement),
                    box(targetStep.startBox), vector(targetMovement));
            if (fraction < bestFraction
                    || (fraction == bestFraction && bestEntity != null && target.getId() < bestEntity.getId())) {
                bestFraction = fraction;
                bestEntity = target;
            }
        }

        if (bestEntity == null || !Double.isFinite(bestFraction)) {
            return null;
        }
        return new EntityHitResult(bestEntity, start.lerp(end, bestFraction));
    }

    private static TargetStep targetStep(Entity projectile, Entity target) {
        AABB currentBox = target.getBoundingBox();
        Vec3 movement;
        AABB startBox;

        // Entity ticking follows insertion order. Older targets normally tick before a newly
        // spawned projectile, so use their observed old-to-current displacement. A rarer target
        // created after the projectile has not ticked yet and is projected by its current velocity.
        if (target.getId() < projectile.getId()) {
            movement = target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
            if (!isSafeTargetStep(movement)) {
                movement = Vec3.ZERO;
            }
            startBox = currentBox.move(movement.reverse());
        } else {
            movement = target.getDeltaMovement();
            if (!isSafeTargetStep(movement)) {
                movement = Vec3.ZERO;
            }
            startBox = currentBox;
        }
        return new TargetStep(startBox, movement);
    }

    private static boolean isSafeTargetStep(Vec3 movement) {
        return Double.isFinite(movement.x) && Double.isFinite(movement.y) && Double.isFinite(movement.z)
                && movement.lengthSqr() <= MAX_TARGET_STEP_SQUARED;
    }

    private static double stepFraction(Entity projectile, Vec3 start, Vec3 tracedMovement) {
        if (projectile.position().distanceToSqr(start) > 1.0E-8) {
            return 1.0;
        }
        Vec3 fullMovement = projectile.getDeltaMovement();
        double fullLengthSquared = fullMovement.lengthSqr();
        if (fullLengthSquared <= 1.0E-12) {
            return 1.0;
        }
        return Math.clamp(tracedMovement.dot(fullMovement) / fullLengthSquared, 0.0, 1.0);
    }

    private static DJProjectileCollisionMath.Vector vector(Vec3 vector) {
        return new DJProjectileCollisionMath.Vector(vector.x, vector.y, vector.z);
    }

    private static DJProjectileCollisionMath.Box box(AABB box) {
        return new DJProjectileCollisionMath.Box(box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ);
    }

    private record TargetStep(AABB startBox, Vec3 movement) {
    }
}

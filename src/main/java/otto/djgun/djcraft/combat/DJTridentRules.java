package otto.djgun.djcraft.combat;

import java.util.List;

import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.util.BeatGridUtil;

public final class DJTridentRules {
    public static final double RETURN_DELAY_BEATS = 3.0;
    public static final double REDIRECT_SPEED = 2.5;
    public static final double MELEE_RADIAL_KNOCKBACK = 1.5;
    public static final int REND_DURATION_TICKS = 160;
    public static final int BASE_REND_LEVEL = 2;
    public static final double MAX_COLLISION_TRACE_DISTANCE = 16.0;
    public static final float COLLISION_BOX_SCALE = 2.0F;
    public static final double RETURN_PICKUP_RADIUS = 0.25;
    public static final double MAX_RETURN_SPEED = 2.5;

    private DJTridentRules() {
    }

    public static int rendAmplifier(int rendingLevel) {
        return BASE_REND_LEVEL + Math.max(0, rendingLevel) - 1;
    }

    public static boolean canCompleteLoyaltyReturn(Vec3 tridentPosition, Vec3 ownerEyePosition) {
        return tridentPosition.distanceToSqr(ownerEyePosition)
                <= RETURN_PICKUP_RADIUS * RETURN_PICKUP_RADIUS;
    }

    public static boolean shouldSkipEntityCollision(boolean noPhysics, boolean returning,
            boolean dealtDamage) {
        return noPhysics || returning || dealtDamage;
    }

    public static boolean shouldUseNoGravity(int loyaltyLevel) {
        return loyaltyLevel > 0;
    }

    public static Vec3 radialKnockbackVelocity(Vec3 currentVelocity, Vec3 attackerEyePosition,
            Vec3 targetCenter, double strength, double knockbackResistance) {
        double effectiveStrength = strength * Math.max(0.0, 1.0 - knockbackResistance);
        if (effectiveStrength <= 0.0) {
            return currentVelocity;
        }
        Vec3 radial = targetCenter.subtract(attackerEyePosition);
        if (radial.lengthSqr() < 1.0E-10) {
            radial = new Vec3(0.0, 1.0, 0.0);
        }
        return currentVelocity.scale(0.5).add(radial.normalize().scale(effectiveStrength));
    }

    public static Vec3 limitReturnVelocity(Vec3 velocity) {
        if (!isFinite(velocity)) {
            return Vec3.ZERO;
        }
        double lengthSqr = velocity.lengthSqr();
        if (lengthSqr <= MAX_RETURN_SPEED * MAX_RETURN_SPEED) {
            return velocity;
        }
        return velocity.normalize().scale(MAX_RETURN_SPEED);
    }

    public static boolean canApplyReturnDamage(boolean available, long gameTime, long startsAtGameTime) {
        return available && gameTime >= startsAtGameTime;
    }

    public static boolean isSafeCollisionTrace(Vec3 start, Vec3 end) {
        if (!isFinite(start) || !isFinite(end)) {
            return false;
        }
        return start.distanceToSqr(end)
                <= MAX_COLLISION_TRACE_DISTANCE * MAX_COLLISION_TRACE_DISTANCE;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static long returnAtTimelineMs(long currentTimeMs, List<BeatEvent> beats) {
        return BeatGridUtil.calculateTargetTime(currentTimeMs, beats, RETURN_DELAY_BEATS);
    }

    public static Vec3 redirectVelocity(Vec3 aimDirection) {
        if (!isFinite(aimDirection) || aimDirection.lengthSqr() < 1.0E-10) {
            aimDirection = new Vec3(0.0, 1.0, 0.0);
        }
        return aimDirection.normalize().scale(REDIRECT_SPEED);
    }
}

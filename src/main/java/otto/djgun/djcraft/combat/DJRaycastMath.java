package otto.djgun.djcraft.combat;

import java.util.OptionalDouble;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Allocation-light segment/AABB intersection shared by ray weapon implementations. */
public final class DJRaycastMath {
    private static final double EPSILON = 1.0E-12;

    private DJRaycastMath() {
    }

    public static OptionalDouble intersectionFraction(Vec3 start, Vec3 end, AABB box) {
        Vec3 delta = end.subtract(start);
        double near = 0.0;
        double far = 1.0;
        double[] origins = { start.x, start.y, start.z };
        double[] directions = { delta.x, delta.y, delta.z };
        double[] mins = { box.minX, box.minY, box.minZ };
        double[] maxs = { box.maxX, box.maxY, box.maxZ };
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < EPSILON) {
                if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) {
                    return OptionalDouble.empty();
                }
                continue;
            }
            double first = (mins[axis] - origins[axis]) / directions[axis];
            double second = (maxs[axis] - origins[axis]) / directions[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.of(near);
    }

    /** Tests the nearest point of a box against a rectangular, view-aligned percentage cone. */
    public static boolean isWithinAimAssist(Vec3 origin, Vec3 look, AABB box, double range,
            double horizontalPercent, double verticalPercent) {
        Vec3 forward = look.normalize();
        Vec3 toCenter = box.getCenter().subtract(origin);
        double centerForwardDistance = toCenter.dot(forward);
        Vec3 pointOnRay = origin.add(forward.scale(centerForwardDistance));
        Vec3 nearestPoint = new Vec3(
                Math.max(box.minX, Math.min(box.maxX, pointOnRay.x)),
                Math.max(box.minY, Math.min(box.maxY, pointOnRay.y)),
                Math.max(box.minZ, Math.min(box.maxZ, pointOnRay.z)));
        Vec3 toTarget = nearestPoint.subtract(origin);
        double forwardDistance = toTarget.dot(forward);
        if (forwardDistance <= 0.0 || forwardDistance > range) {
            return false;
        }
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < EPSILON) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();
        double horizontalLimit = forwardDistance * horizontalPercent / 100.0;
        double verticalLimit = forwardDistance * verticalPercent / 100.0;
        return Math.abs(toTarget.dot(right)) <= horizontalLimit
                && Math.abs(toTarget.dot(up)) <= verticalLimit;
    }
}

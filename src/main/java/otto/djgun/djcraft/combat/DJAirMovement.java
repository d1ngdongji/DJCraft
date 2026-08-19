package otto.djgun.djcraft.combat;

import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.network.packet.DJDashDirection;

/** Pure air-movement vector composition shared by tests and server authority. */
public final class DJAirMovement {
    private static final double FORTY_FIVE_DEGREE_COMPONENT = 1.0 / Math.sqrt(2.0);

    private DJAirMovement() {
    }

    public static Vec3 doubleJump(DJDashDirection direction, float yawDegrees, double momentum) {
        double clampedMomentum = Math.max(0.0, momentum);
        if (direction == DJDashDirection.NONE) {
            return new Vec3(0.0, clampedMomentum, 0.0);
        }

        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double worldX = direction.strafe() * cos - direction.forward() * sin;
        double worldZ = direction.forward() * cos + direction.strafe() * sin;
        Vec3 horizontal = new Vec3(worldX, 0.0, worldZ).normalize()
                .scale(clampedMomentum * FORTY_FIVE_DEGREE_COMPONENT);
        return new Vec3(horizontal.x, clampedMomentum * FORTY_FIVE_DEGREE_COMPONENT, horizontal.z);
    }

    public static Vec3 groundSlam() {
        return new Vec3(0.0, -DJMovementAbilityRules.GROUND_SLAM_DOWNWARD_VELOCITY, 0.0);
    }
}

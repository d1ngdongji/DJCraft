package otto.djgun.djcraft.combat;

import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.network.packet.DJDashDirection;

/** Pure dash-vector composition shared by client prediction and server authority. */
public final class DJDashMomentum {
    private DJDashMomentum() {
    }

    public static Vec3 compose(Vec3 currentVelocity, DJDashDirection direction,
            float yawDegrees, double directionalImpulse) {
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double worldX = direction.strafe() * cos - direction.forward() * sin;
        double worldZ = direction.forward() * cos + direction.strafe() * sin;
        Vec3 input = new Vec3(worldX, 0.0, worldZ);
        if (input.lengthSqr() > 0.0) {
            input = input.normalize().scale(directionalImpulse);
        }
        return new Vec3(currentVelocity.x + input.x, 0.0, currentVelocity.z + input.z);
    }

    public static Vec3 addAirImpulse(Vec3 dashMomentum, Vec3 airImpulse) {
        return dashMomentum.add(airImpulse);
    }
}

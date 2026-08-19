package otto.djgun.djcraft.combat;

import net.minecraft.world.phys.Vec3;

/** Direction test shared by DJ parry and shield-triggered deferred damage. */
final class DJShieldFacingRules {
    private DJShieldFacingRules() {
    }

    static boolean isFacing(Vec3 viewVector, Vec3 defenderPosition, Vec3 causingEntityPosition) {
        if (causingEntityPosition == null) {
            return false;
        }
        Vec3 sourceToDefender = causingEntityPosition.vectorTo(defenderPosition).normalize();
        Vec3 horizontalDirection = new Vec3(sourceToDefender.x, 0.0, sourceToDefender.z);
        return horizontalDirection.dot(viewVector) < 0.0;
    }
}

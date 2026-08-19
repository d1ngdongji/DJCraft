package otto.djgun.djcraft.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJSoftTargetMeleeBehavior;

/** Minecraft adapter around the pure continuously swept melee collision math. */
public final class DJSweptMeleeVolume {
    private final DJSweptMeleeMath.Volume volume;
    private final AABB bounds;

    private DJSweptMeleeVolume(DJSweptMeleeMath.Volume volume) {
        this.volume = volume;
        DJSweptMeleeMath.Box value = volume.bounds();
        this.bounds = new AABB(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    public static DJSweptMeleeVolume create(Vec3 startEye, Vec3 endEye, Vec3 look,
            DJMeleeBehavior behavior) {
        DJSweptMeleeMath.Volume volume;
        if (behavior instanceof DJAreaMeleeBehavior area) {
            volume = DJSweptMeleeMath.oneEndedCapsule(vector(startEye), vector(endEye), vector(look),
                    area.cylinderLength(), area.radius());
        } else {
            DJSoftTargetMeleeBehavior soft = (DJSoftTargetMeleeBehavior) behavior;
            volume = DJSweptMeleeMath.softCone(vector(startEye), vector(endEye), vector(look),
                    soft.reach(), soft.horizontalAngleDegrees(), soft.verticalAngleDegrees());
        }
        return new DJSweptMeleeVolume(volume);
    }

    public static DJSweptMeleeVolume directRay(Vec3 startEye, Vec3 endEye, Vec3 look, double reach) {
        return new DJSweptMeleeVolume(DJSweptMeleeMath.directRay(
                vector(startEye), vector(endEye), vector(look), reach));
    }

    public AABB bounds() {
        return bounds;
    }

    public boolean intersects(AABB box) {
        return volume.intersects(new DJSweptMeleeMath.Box(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
    }

    private static DJSweptMeleeMath.Vector vector(Vec3 value) {
        return new DJSweptMeleeMath.Vector(value.x, value.y, value.z);
    }
}

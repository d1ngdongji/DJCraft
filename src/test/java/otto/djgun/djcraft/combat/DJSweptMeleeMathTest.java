package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJSweptMeleeMathTest {
    private static final DJSweptMeleeMath.Vector ORIGIN = vector(0.0, 0.0, 0.0);
    private static final DJSweptMeleeMath.Vector FORWARD = vector(0.0, 0.0, 1.0);

    @Test
    void softConeHonorsReachAndSeparateAngleBoundaries() {
        var volume = DJSweptMeleeMath.softCone(ORIGIN, ORIGIN, FORWARD, 4.25, 30.0, 20.0);

        assertTrue(volume.intersects(box(-0.1, -0.1, 4.1, 0.1, 0.1, 4.24)));
        assertFalse(volume.intersects(box(-0.1, -0.1, 4.26, 0.1, 0.1, 4.5)));
        assertTrue(volume.intersects(box(2.25, -0.05, 3.95, 2.30, 0.05, 4.05)));
        assertFalse(volume.intersects(box(2.36, -0.05, 3.95, 2.45, 0.05, 4.05)));
        assertTrue(volume.intersects(box(-0.05, 1.42, 3.95, 0.05, 1.46, 4.05)));
        assertFalse(volume.intersects(box(-0.05, 1.50, 3.95, 0.05, 1.58, 4.05)));
    }

    @Test
    void sweptRayFindsMidPathContactWithoutEndpointOverlap() {
        var swept = DJSweptMeleeMath.directRay(vector(-2.0, 0.0, 0.0),
                vector(2.0, 0.0, 0.0), FORWARD, 4.25);
        var startOnly = DJSweptMeleeMath.directRay(vector(-2.0, 0.0, 0.0),
                vector(-2.0, 0.0, 0.0), FORWARD, 4.25);
        var endOnly = DJSweptMeleeMath.directRay(vector(2.0, 0.0, 0.0),
                vector(2.0, 0.0, 0.0), FORWARD, 4.25);
        var target = box(-0.1, -0.1, 2.0, 0.1, 0.1, 2.2);

        assertTrue(swept.intersects(target));
        assertFalse(startOnly.intersects(target));
        assertFalse(endOnly.intersects(target));
    }

    @Test
    void oneEndedCapsuleHasFlatNearEndAndRoundedFarEnd() {
        var capsule = DJSweptMeleeMath.oneEndedCapsule(ORIGIN, ORIGIN, FORWARD, 5.0, 1.0);

        assertTrue(capsule.intersects(box(0.90, -0.1, 2.0, 0.99, 0.1, 2.2)));
        assertFalse(capsule.intersects(box(1.05, -0.1, 2.0, 1.15, 0.1, 2.2)));
        assertTrue(capsule.intersects(box(-0.1, -0.1, 5.85, 0.1, 0.1, 5.95)));
        assertFalse(capsule.intersects(box(-0.1, -0.1, -0.6, 0.1, 0.1, -0.1)));
        assertFalse(capsule.intersects(box(0.85, -0.1, 5.55, 0.95, 0.1, 5.65)));
    }

    @Test
    void pitchedCapsuleRotatesItsCompleteShape() {
        var capsule = DJSweptMeleeMath.oneEndedCapsule(ORIGIN, ORIGIN,
                vector(0.0, 1.0, 0.0), 2.0, 2.0);

        assertTrue(capsule.intersects(box(-0.1, 3.8, -0.1, 0.1, 3.95, 0.1)));
        assertFalse(capsule.intersects(box(-0.1, -1.0, -0.1, 0.1, -0.2, 0.1)));
    }

    private static DJSweptMeleeMath.Vector vector(double x, double y, double z) {
        return new DJSweptMeleeMath.Vector(x, y, z);
    }

    private static DJSweptMeleeMath.Box box(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        return new DJSweptMeleeMath.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

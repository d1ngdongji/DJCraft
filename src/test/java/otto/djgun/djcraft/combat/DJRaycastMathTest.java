package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class DJRaycastMathTest {
    @Test
    void findsOrderedEntryFractionsAlongSegment() {
        Vec3 start = new Vec3(0.0, 1.0, 0.0);
        Vec3 end = new Vec3(0.0, 1.0, 10.0);
        double near = DJRaycastMath.intersectionFraction(start, end,
                new AABB(-0.5, 0.0, 2.0, 0.5, 2.0, 3.0)).orElseThrow();
        double far = DJRaycastMath.intersectionFraction(start, end,
                new AABB(-0.5, 0.0, 7.0, 0.5, 2.0, 8.0)).orElseThrow();
        assertEquals(0.2, near, 1.0E-9);
        assertEquals(0.7, far, 1.0E-9);
        assertTrue(near < far);
    }

    @Test
    void rejectsBoxesOutsideRayAndPastClippedEndpoint() {
        Vec3 start = new Vec3(0.0, 0.0, 0.0);
        Vec3 clippedEnd = new Vec3(0.0, 0.0, 5.0);
        assertTrue(DJRaycastMath.intersectionFraction(start, clippedEnd,
                new AABB(1.0, -0.5, 2.0, 2.0, 0.5, 3.0)).isEmpty());
        assertTrue(DJRaycastMath.intersectionFraction(start, clippedEnd,
                new AABB(-0.5, -0.5, 6.0, 0.5, 0.5, 7.0)).isEmpty());
    }

    @Test
    void aimAssistUsesIndependentHorizontalAndVerticalPercentages() {
        Vec3 origin = Vec3.ZERO;
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        assertTrue(DJRaycastMath.isWithinAimAssist(origin, look,
                centeredBox(0.69, 0.69, 10.0), 64.0, 7.0, 7.0));
        assertTrue(!DJRaycastMath.isWithinAimAssist(origin, look,
                centeredBox(0.81, 0.0, 10.0), 64.0, 7.0, 7.0));
        assertTrue(!DJRaycastMath.isWithinAimAssist(origin, look,
                centeredBox(0.0, 0.81, 10.0), 64.0, 7.0, 7.0));
    }

    @Test
    void aimAssistRejectsTargetsBehindViewOrBeyondRange() {
        Vec3 origin = Vec3.ZERO;
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        assertTrue(!DJRaycastMath.isWithinAimAssist(origin, look,
                centeredBox(0.0, 0.0, -2.0), 64.0, 7.0, 7.0));
        assertTrue(!DJRaycastMath.isWithinAimAssist(origin, look,
                centeredBox(0.0, 0.0, 65.0), 64.0, 7.0, 7.0));
    }

    private static AABB centeredBox(double x, double y, double z) {
        return new AABB(x - 0.1, y - 0.1, z - 0.1, x + 0.1, y + 0.1, z + 0.1);
    }
}

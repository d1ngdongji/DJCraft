package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJProjectileCollisionMathTest {
    @Test
    void detectsOpposingBodiesThatExchangeSidesDuringOneTick() {
        double fraction = collision(
                box(-0.375, -0.375, -0.375, 0.375, 0.375, 0.375), vector(2.0, 0.0, 0.0),
                box(2.75, -0.5, -0.5, 3.25, 0.5, 0.5), vector(-2.0, 0.0, 0.0));

        assertEquals(0.59375, fraction, 1.0E-9);
    }

    @Test
    void usesProjectileBoxInsteadOfOnlyItsCenterLine() {
        double fraction = collision(
                box(-0.375, 0.0, -0.375, 0.375, 0.75, 0.375), vector(2.0, 0.0, 0.0),
                box(1.0, 0.70, -0.2, 1.4, 1.8, 0.2), vector(0.0, 0.0, 0.0));

        assertTrue(Double.isFinite(fraction));
    }

    @Test
    void rejectsSeparatedParallelSweeps() {
        double fraction = collision(
                box(-0.25, 0.0, -0.25, 0.25, 0.5, 0.25), vector(2.0, 0.0, 0.0),
                box(1.0, 1.0, -0.2, 1.4, 2.0, 0.2), vector(2.0, 0.0, 0.0));

        assertEquals(Double.POSITIVE_INFINITY, fraction);
    }

    private static double collision(DJProjectileCollisionMath.Box projectile,
            DJProjectileCollisionMath.Vector projectileMovement,
            DJProjectileCollisionMath.Box target, DJProjectileCollisionMath.Vector targetMovement) {
        return DJProjectileCollisionMath.firstContactFraction(projectile, projectileMovement,
                target, targetMovement);
    }

    private static DJProjectileCollisionMath.Vector vector(double x, double y, double z) {
        return new DJProjectileCollisionMath.Vector(x, y, z);
    }

    private static DJProjectileCollisionMath.Box box(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        return new DJProjectileCollisionMath.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

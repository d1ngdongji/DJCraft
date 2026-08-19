package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.network.packet.DJDashDirection;

class DJDashMomentumTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void ordinaryAndFloweryUseDistinctMomentumLockDurations() {
        assertEquals(3, DJMovementAbilityRules.NORMAL_DASH_MOMENTUM_LOCK_TICKS);
        assertEquals(5, DJMovementAbilityRules.FLOWERY_DASH_MOMENTUM_LOCK_TICKS);
    }

    @Test
    void directionalImpulseMultipliersDoNotScaleExistingMomentum() {
        Vec3 current = new Vec3(0.4, 0.8, -0.2);
        Vec3 ordinary = DJDashMomentum.compose(current, DJDashDirection.FORWARD, 0.0F,
                2.0 * DJMovementAbilityRules.NORMAL_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER);
        Vec3 flowery = DJDashMomentum.compose(current, DJDashDirection.FORWARD, 0.0F,
                2.0 * DJMovementAbilityRules.FLOWERY_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER);

        assertEquals(0.4, ordinary.x, EPSILON);
        assertEquals(1.3, ordinary.z, EPSILON);
        assertEquals(0.4, flowery.x, EPSILON);
        assertEquals(1.4, flowery.z, EPSILON);
    }

    @Test
    void addsDirectionalImpulseToExistingHorizontalMomentumAndDropsVerticalMomentum() {
        Vec3 result = DJDashMomentum.compose(
                new Vec3(0.25, -0.7, -0.5), DJDashDirection.FORWARD, 0.0F, 1.5);

        assertEquals(0.25, result.x, EPSILON);
        assertEquals(0.0, result.y, EPSILON);
        assertEquals(1.0, result.z, EPSILON);
    }

    @Test
    void normalizesDiagonalInputBeforeAddingImpulse() {
        Vec3 result = DJDashMomentum.compose(
                Vec3.ZERO, DJDashDirection.FORWARD_RIGHT, 90.0F, 2.0);
        double component = Math.sqrt(2.0);

        assertEquals(-component, result.x, EPSILON);
        assertEquals(-component, result.z, EPSILON);
    }

    @Test
    void doubleJumpImpulseStacksWithLockedDashMomentum() {
        Vec3 result = DJDashMomentum.addAirImpulse(
                new Vec3(1.5, 0.0, -0.25), new Vec3(0.75, 0.8, 0.5));

        assertEquals(2.25, result.x, EPSILON);
        assertEquals(0.8, result.y, EPSILON);
        assertEquals(0.25, result.z, EPSILON);
    }
}

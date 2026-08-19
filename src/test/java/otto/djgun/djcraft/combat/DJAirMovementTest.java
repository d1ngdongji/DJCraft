package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.network.packet.DJDashDirection;

import java.util.UUID;

class DJAirMovementTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void doubleJumpCostsNoEnergy() {
        assertEquals(0.0, DJMovementAbilityRules.DOUBLE_JUMP_ENERGY_COST, EPSILON);
        assertEquals(1.8, DJMovementAbilityRules.DOUBLE_JUMP_MOMENTUM_MULTIPLIER, EPSILON);
    }

    @Test
    void noInputSendsAllMomentumUpward() {
        Vec3 result = DJAirMovement.doubleJump(DJDashDirection.NONE, 37.0F, 2.0);

        assertEquals(0.0, result.x, EPSILON);
        assertEquals(2.0, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
        assertEquals(2.0, result.length(), EPSILON);
    }

    @Test
    void allEightDirectionsKeepMagnitudeAndFortyFiveDegreePitch() {
        for (DJDashDirection direction : DJDashDirection.values()) {
            if (direction == DJDashDirection.NONE) {
                continue;
            }
            Vec3 result = DJAirMovement.doubleJump(direction, 31.0F, 2.0);

            assertEquals(2.0, result.length(), EPSILON, direction.name());
            assertEquals(Math.sqrt(2.0), result.y, EPSILON, direction.name());
            assertEquals(Math.sqrt(2.0), result.horizontalDistance(), EPSILON, direction.name());
        }
    }

    @Test
    void groundSlamClearsHorizontalMomentumAndMovesStronglyDownward() {
        Vec3 result = DJAirMovement.groundSlam();

        assertEquals(0.0, result.x, EPSILON);
        assertEquals(-3.0, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    void fallDamageImmunityIsConsumedExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        DJFallDamageImmunity.arm(playerId);

        assertTrue(DJFallDamageImmunity.consume(playerId));
        assertFalse(DJFallDamageImmunity.consume(playerId));
    }
}

package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class DJShieldFacingRulesTest {
    private static final Vec3 DEFENDER = Vec3.ZERO;
    private static final Vec3 LOOK_SOUTH = new Vec3(0.0, 0.0, 1.0);

    @Test
    void acceptsCausingEntitiesInFrontAndRejectsCausingEntitiesBehind() {
        assertTrue(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, new Vec3(0.0, 0.0, 2.0)));
        assertFalse(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, new Vec3(0.0, 0.0, -2.0)));
    }

    @Test
    void appliesVanillaShieldHorizontalHemisphereToCausingEntityPosition() {
        assertTrue(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, new Vec3(1.0, 8.0, 0.001)));
        assertFalse(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, new Vec3(1.0, 8.0, 0.0)));
        assertFalse(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, new Vec3(1.0, 8.0, -0.001)));
        assertFalse(DJShieldFacingRules.isFacing(LOOK_SOUTH, DEFENDER, null));
    }
}

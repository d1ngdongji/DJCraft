package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DJGroundSlamRulesTest {
    @Test
    void groundSlamImpactUsesRequestedRangeAndLaunchSpeed() {
        assertEquals(3.0, DJMovementAbilityRules.GROUND_SLAM_MIN_EFFECT_FALL_DISTANCE, 0.0001);
        assertEquals(4.0, DJMovementAbilityRules.GROUND_SLAM_RADIUS, 0.0001);
        assertEquals(1.0, DJMovementAbilityRules.GROUND_SLAM_LAUNCH_SPEED, 0.0001);
    }

    @Test
    void damageScalesLinearlyFromThreeToTwelveBlocks() {
        assertEquals(4.0F, DJMovementAbilityRules.groundSlamDamage(3.0), 0.0001F);
        assertEquals(10.0F, DJMovementAbilityRules.groundSlamDamage(7.5), 0.0001F);
        assertEquals(16.0F, DJMovementAbilityRules.groundSlamDamage(12.0), 0.0001F);
    }

    @Test
    void damageClampsOutsideTheRewardRange() {
        assertEquals(4.0F, DJMovementAbilityRules.groundSlamDamage(2.0), 0.0001F);
        assertEquals(16.0F, DJMovementAbilityRules.groundSlamDamage(40.0), 0.0001F);
    }
}

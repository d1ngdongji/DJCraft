package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class DJRayExplosionMathTest {
    @Test
    void damageStrengthFallsOffLinearlyAndRespectsExposure() {
        assertEquals(1.0, DJRayWeaponCombatService.explosionStrength(0.0, 4.0, 1.0), 1.0E-9);
        assertEquals(0.5, DJRayWeaponCombatService.explosionStrength(2.0, 4.0, 1.0), 1.0E-9);
        assertEquals(0.25, DJRayWeaponCombatService.explosionStrength(2.0, 4.0, 0.5), 1.0E-9);
        assertEquals(0.0, DJRayWeaponCombatService.explosionStrength(4.0, 4.0, 1.0), 1.0E-9);
    }

    @Test
    void distanceUsesNearestPointOnEntityBounds() {
        AABB box = new AABB(2.0, 1.0, -1.0, 4.0, 3.0, 1.0);
        assertEquals(new Vec3(2.0, 2.0, 0.0),
                DJRayWeaponCombatService.closestPoint(box, new Vec3(0.0, 2.0, 0.0)));
        assertEquals(new Vec3(3.0, 2.0, 0.0),
                DJRayWeaponCombatService.closestPoint(box, new Vec3(3.0, 2.0, 0.0)));
    }

    @Test
    void nonAlliedSurvivalPlayersRemainLegalExplosionTargets() {
        assertTrue(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, false, false, false, true, false));
        assertFalse(DJRayWeaponCombatService.isEligiblePlayerTarget(
                true, false, false, false, true, false));
        assertFalse(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, true, false, false, true, false));
        assertFalse(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, false, true, false, true, false));
        assertFalse(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, false, false, true, true, false));
        assertFalse(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, false, false, false, false, false));
        assertTrue(DJRayWeaponCombatService.isEligiblePlayerTarget(
                false, false, false, false, false, true));
    }

    @Test
    void playerExplosionBypassesOnlyTheDisabledVanillaPvpGate() {
        assertTrue(DJRayWeaponCombatService.shouldBypassVanillaPvp(true, false));
        assertFalse(DJRayWeaponCombatService.shouldBypassVanillaPvp(true, true));
        assertFalse(DJRayWeaponCombatService.shouldBypassVanillaPvp(false, false));
    }
}

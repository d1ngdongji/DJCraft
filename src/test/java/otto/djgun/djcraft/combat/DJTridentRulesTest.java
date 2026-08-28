package otto.djgun.djcraft.combat;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.data.BeatEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DJTridentRulesTest {
    @Test
    void rendingAddsLevelsToTheBuiltInRendTwo() {
        assertEquals(1, DJTridentRules.rendAmplifier(0));
        assertEquals(2, DJTridentRules.rendAmplifier(1));
        assertEquals(4, DJTridentRules.rendAmplifier(3));
    }

    @Test
    void returnDeadlineUsesThreeContinuousVirtualBeatsAcrossTempoChanges() {
        List<BeatEvent> beats = List.of(
                beat(0), beat(500), beat(1000), beat(1500), beat(1750), beat(2000), beat(2250));

        assertEquals(1625L, DJTridentRules.returnAtTimelineMs(250L, beats));
        assertEquals(1812L, DJTridentRules.returnAtTimelineMs(625L, beats));
    }

    @Test
    void redirectVelocityFollowsAimAndHasThrowSpeed() {
        assertEquals(1.5, DJTridentRules.MELEE_RADIAL_KNOCKBACK);
        Vec3 velocity = DJTridentRules.redirectVelocity(new Vec3(3.0, 4.0, 0.0));

        assertEquals(DJTridentRules.REDIRECT_SPEED, velocity.length(), 1.0E-9);
        assertEquals(1.5, velocity.x, 1.0E-9);
        assertEquals(2.0, velocity.y, 1.0E-9);
        assertEquals(0.0, velocity.z, 1.0E-9);
    }

    @Test
    void redirectVelocityFallsBackUpForInvalidAim() {
        Vec3 velocity = DJTridentRules.redirectVelocity(Vec3.ZERO);

        assertEquals(new Vec3(0.0, DJTridentRules.REDIRECT_SPEED, 0.0), velocity);
        assertEquals(velocity, DJTridentRules.redirectVelocity(
                new Vec3(Double.NaN, 0.0, 0.0)));
    }

    @Test
    void collisionTraceRejectsNonFiniteAndAbnormallyLongSegments() {
        assertTrue(DJTridentRules.isSafeCollisionTrace(Vec3.ZERO, new Vec3(2.5, 0.0, 0.0)));
        assertFalse(DJTridentRules.isSafeCollisionTrace(Vec3.ZERO, new Vec3(17.0, 0.0, 0.0)));
        assertFalse(DJTridentRules.isSafeCollisionTrace(Vec3.ZERO,
                new Vec3(Double.POSITIVE_INFINITY, 0.0, 0.0)));
        assertFalse(DJTridentRules.isSafeCollisionTrace(Vec3.ZERO,
                new Vec3(Double.NaN, 0.0, 0.0)));
    }

    @Test
    void loyaltyPickupRequiresTheTridentCenterToReachTheOwner() {
        assertEquals(2.0F, DJTridentRules.COLLISION_BOX_SCALE);
        assertTrue(DJTridentRules.canCompleteLoyaltyReturn(
                new Vec3(0.2, 1.62, 0.0), new Vec3(0.0, 1.62, 0.0)));
        assertFalse(DJTridentRules.canCompleteLoyaltyReturn(
                new Vec3(0.3, 1.62, 0.0), new Vec3(0.0, 1.62, 0.0)));
    }

    @Test
    void returningNoPhysicsOrAlreadyHitTridentsSkipEntityCollisionQueries() {
        assertFalse(DJTridentRules.shouldSkipEntityCollision(false, false, false));
        assertTrue(DJTridentRules.shouldSkipEntityCollision(true, false, false));
        assertTrue(DJTridentRules.shouldSkipEntityCollision(false, true, false));
        assertTrue(DJTridentRules.shouldSkipEntityCollision(false, false, true));
        assertTrue(DJTridentRules.shouldSkipEntityCollision(true, true, true));
    }

    @Test
    void onlyLoyaltyTridentsUseNoGravityFlight() {
        assertFalse(DJTridentRules.shouldUseNoGravity(0));
        assertTrue(DJTridentRules.shouldUseNoGravity(1));
        assertTrue(DJTridentRules.shouldUseNoGravity(3));
    }

    @Test
    void radialMeleeKnockbackIncludesVerticalDirectionAndResistance() {
        Vec3 launched = DJTridentRules.radialKnockbackVelocity(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 4.0, 0.0),
                DJTridentRules.MELEE_RADIAL_KNOCKBACK, 0.0);
        assertEquals(0.0, launched.x, 1.0E-9);
        assertEquals(1.5, launched.y, 1.0E-9);
        assertEquals(0.0, launched.z, 1.0E-9);

        Vec3 resistant = DJTridentRules.radialKnockbackVelocity(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 4.0, 0.0),
                DJTridentRules.MELEE_RADIAL_KNOCKBACK, 0.6);
        assertEquals(0.6, resistant.y, 1.0E-9);
        assertEquals(Vec3.ZERO, DJTridentRules.radialKnockbackVelocity(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 4.0, 0.0),
                DJTridentRules.MELEE_RADIAL_KNOCKBACK, 1.0));
    }

    @Test
    void returnVelocityIsFiniteAndCappedForSmoothRendering() {
        assertEquals(new Vec3(0.0, 0.0, 2.0),
                DJTridentRules.limitReturnVelocity(new Vec3(0.0, 0.0, 2.0)));
        Vec3 capped = DJTridentRules.limitReturnVelocity(new Vec3(0.0, 3.0, 4.0));
        assertEquals(DJTridentRules.MAX_RETURN_SPEED, capped.length(), 1.0E-9);
        assertEquals(Vec3.ZERO, DJTridentRules.limitReturnVelocity(
                new Vec3(Double.POSITIVE_INFINITY, 0.0, 0.0)));
    }

    @Test
    void returnDamageWaitsUntilTheReturnHasActuallyStarted() {
        assertFalse(DJTridentRules.canApplyReturnDamage(false, 20L, 20L));
        assertFalse(DJTridentRules.canApplyReturnDamage(true, 19L, 20L));
        assertTrue(DJTridentRules.canApplyReturnDamage(true, 20L, 20L));
    }

    private static BeatEvent beat(long timeMs) {
        return new BeatEvent((int) timeMs, "beat");
    }
}

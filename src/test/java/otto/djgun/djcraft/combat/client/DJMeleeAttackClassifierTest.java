package otto.djgun.djcraft.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;

class DJMeleeAttackClassifierTest {
    @Test
    void sprintAttackRequiresVanillaStrongAttackThreshold() {
        assertEquals(DJAnimationEvent.Kind.MELEE_THRUST,
                classify(true, true, true, false, true, 0.0, 0.1f, true));
        assertEquals(DJAnimationEvent.Kind.MELEE_STRIKE,
                classify(true, false, true, false, true, 0.0, 0.1f, true));
    }

    @Test
    void criticalAttackUsesItsOwnVanillaPredicate() {
        assertEquals(DJAnimationEvent.Kind.MELEE_CRITICAL,
                classify(true, true, false, true, false, 0.0, 0.1f, true));
    }

    @Test
    void sweepRequiresGroundedSlowMovementAndSwordSweepAbility() {
        assertEquals(DJAnimationEvent.Kind.MELEE_SWEEP,
                classify(true, true, false, false, true, 0.05, 0.1f, true));
        assertEquals(DJAnimationEvent.Kind.MELEE_STRIKE,
                classify(true, true, false, false, true, 0.11, 0.1f, true));
        assertEquals(DJAnimationEvent.Kind.MELEE_STRIKE,
                classify(true, true, false, false, true, 0.05, 0.1f, false));
    }

    private static DJAnimationEvent.Kind classify(boolean target, boolean strong, boolean sprinting,
            boolean critical, boolean onGround, double walked, float speed, boolean canSweep) {
        return DJMeleeAttackClassifier.classifyVanillaFlags(
                target, strong, sprinting, critical, onGround, walked, speed, canSweep);
    }
}

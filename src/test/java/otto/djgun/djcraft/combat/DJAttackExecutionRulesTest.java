package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJAttackExecutionRulesTest {
    @Test
    void validMissUsesClampedRemainingDamagePercent() {
        HitResult miss = HitResult.miss(1000L);

        assertFalse(DJAttackExecutionRules.canExecute(true, miss, 0));
        assertTrue(DJAttackExecutionRules.canExecute(true, miss, 25));
        assertEquals(0.25F, DJAttackExecutionRules.offBeatDamageMultiplier(25));
        assertEquals(1.0F, DJAttackExecutionRules.offBeatDamageMultiplier(150));
    }

    @Test
    void invalidActionCannotBeRescuedByTheGameRule() {
        assertFalse(DJAttackExecutionRules.canExecute(false, HitResult.miss(1000L), 100));
    }

    @Test
    void beatHitAlwaysExecutesEvenWhenOffBeatDamageIsDisabled() {
        HitResult hit = new HitResult(true, null, null, 1, 1000L);

        assertTrue(DJAttackExecutionRules.canExecute(true, hit, 0));
    }
}

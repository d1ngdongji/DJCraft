package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJComboAbsorptionRulesTest {
    @Test
    void rewardsEveryTenEarnedCombos() {
        assertFalse(DJComboAbsorptionRules.shouldReward(0));
        assertFalse(DJComboAbsorptionRules.shouldReward(9));
        assertTrue(DJComboAbsorptionRules.shouldReward(10));
        assertFalse(DJComboAbsorptionRules.shouldReward(19));
        assertTrue(DJComboAbsorptionRules.shouldReward(20));
        assertTrue(DJComboAbsorptionRules.shouldReward(50));
    }

    @Test
    void usesThirtySecondsOfAbsorptionTwo() {
        assertEquals(600, DJComboAbsorptionRules.DURATION_TICKS);
        assertEquals(1, DJComboAbsorptionRules.AMPLIFIER);
    }
}

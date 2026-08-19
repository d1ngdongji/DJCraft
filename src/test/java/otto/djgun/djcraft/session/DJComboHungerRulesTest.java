package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJComboHungerRulesTest {
    @Test
    void rewardsEverySecondComboBelowFifty() {
        assertFalse(DJComboHungerRules.shouldReward(0));
        assertFalse(DJComboHungerRules.shouldReward(1));
        assertTrue(DJComboHungerRules.shouldReward(2));
        assertTrue(DJComboHungerRules.shouldReward(48));
        assertFalse(DJComboHungerRules.shouldReward(49));
    }

    @Test
    void rewardsEveryComboFromFiftyOnward() {
        assertTrue(DJComboHungerRules.shouldReward(50));
        assertTrue(DJComboHungerRules.shouldReward(51));
        assertTrue(DJComboHungerRules.shouldReward(99));
    }
}

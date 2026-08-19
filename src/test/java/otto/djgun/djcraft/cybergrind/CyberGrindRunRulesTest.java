package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CyberGrindRunRulesTest {
    @Test
    void requiresWarningsGracePeriodAndStrictlyLowerResidualCost() {
        assertFalse(CyberGrindRunRules.canAdvance(false, 200, 200, 0, 5));
        assertFalse(CyberGrindRunRules.canAdvance(true, 199, 200, 0, 5));
        assertFalse(CyberGrindRunRules.canAdvance(true, 200, 200, 5, 5));
        assertTrue(CyberGrindRunRules.canAdvance(true, 200, 200, 4, 5));
    }

    @Test
    void crossingArenaBoundsDoesNotEliminatePlayer() {
        assertFalse(CyberGrindRunRules.shouldEliminateForLocation(true, false));
        assertFalse(CyberGrindRunRules.shouldEliminateForLocation(true, true));
        assertTrue(CyberGrindRunRules.shouldEliminateForLocation(false, false));
    }
}

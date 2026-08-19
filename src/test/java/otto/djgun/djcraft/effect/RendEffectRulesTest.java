package otto.djgun.djcraft.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendEffectRulesTest {
    @Test
    void addsTwoDamagePerDisplayedLevel() {
        assertEquals(2.0F, RendEffectRules.bonusDamage(0));
        assertEquals(4.0F, RendEffectRules.bonusDamage(1));
        assertEquals(6.0F, RendEffectRules.bonusDamage(2));
    }

    @Test
    void onlyServerCombatDamageIsAmplified() {
        assertTrue(RendEffectRules.shouldAmplify(false, true, 1.0F));
        assertFalse(RendEffectRules.shouldAmplify(false, false, 1.0F));
        assertFalse(RendEffectRules.shouldAmplify(true, true, 1.0F));
        assertFalse(RendEffectRules.shouldAmplify(false, true, 0.0F));
    }
}

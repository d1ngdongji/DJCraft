package otto.djgun.djcraft.init;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.GameRules;

class ModGameRulesTest {
    @Test
    void newlyConstructedRulesUseGameplayDefaults() {
        ModGameRules.bootstrap();
        GameRules rules = new GameRules();

        assertEquals(50, ModGameRules.offBeatAttackDamagePercent(rules));
        assertEquals(2, ModGameRules.baseMaxToleranceChances(rules));
        assertEquals(5, ModGameRules.idleAttackableBeatsBeforeComboReset(rules));
        assertEquals(3, ModGameRules.maxToleranceChances(rules, 1));
        assertEquals(7, ModGameRules.idleAttackableBeatsBeforeComboReset(rules, 2));
    }

    @Test
    void runtimeValuesAreClampedToSupportedRanges() {
        assertEquals(0, ModGameRules.sanitizeOffBeatAttackDamagePercent(-1));
        assertEquals(25, ModGameRules.sanitizeOffBeatAttackDamagePercent(25));
        assertEquals(100, ModGameRules.sanitizeOffBeatAttackDamagePercent(101));
        assertEquals(0, ModGameRules.sanitizeBaseMaxToleranceChances(-1));
        assertEquals(16, ModGameRules.sanitizeBaseMaxToleranceChances(17));
        assertEquals(1, ModGameRules.sanitizeIdleAttackableBeatsBeforeComboReset(0));
    }
}

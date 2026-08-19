package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;

class DJMeleeBehaviorResolverTest {
    @Test
    void ordinaryLeftClickUsesMeleeBehaviorInsteadOfItemUseBehavior() {
        assertSame(DJItemBehaviorRegistry.MELEE,
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(DJItemBehaviorRegistry.SHIELD));
        assertSame(DJItemBehaviorRegistry.MELEE,
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(DJItemBehaviorRegistry.BOW));
    }

    @Test
    void registeredAreaLeftClickUsesItsActualBehavior() {
        assertSame(DJItemBehaviorRegistry.TRIDENT,
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(DJItemBehaviorRegistry.TRIDENT));
        assertSame(DJItemBehaviorRegistry.MACE,
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(DJItemBehaviorRegistry.MACE));
        assertTrue(DJItemBehaviorRegistry.TRIDENT.meleeBehavior().orElseThrow()
                instanceof DJAreaMeleeBehavior);
    }
}

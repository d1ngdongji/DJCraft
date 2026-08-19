package otto.djgun.djcraft.combat;

import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.api.combat.DJItemBehaviorDefinition;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;

/** Keeps left-click behavior policy and its registered executor aligned on both sides. */
public final class DJMeleeBehaviorResolver {
    private DJMeleeBehaviorResolver() {
    }

    public static DJItemBehaviorDefinition resolveJudgmentBehavior(ItemStack stack) {
        return resolveJudgmentBehavior(DJItemBehaviorManager.resolveDefinition(stack));
    }

    static DJItemBehaviorDefinition resolveJudgmentBehavior(DJItemBehaviorDefinition itemBehavior) {
        return itemBehavior.meleeBehavior().filter(DJMeleeBehavior::area).isPresent()
                ? itemBehavior
                : DJItemBehaviorRegistry.MELEE;
    }

    public static DJMeleeBehavior resolveExecutor(ItemStack stack) {
        return DJItemBehaviorManager.resolveMeleeBehavior(stack);
    }
}

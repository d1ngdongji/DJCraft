package otto.djgun.djcraft.combat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Shared classification rule for block-targeted attack input. */
public final class DJMiningRules {
    private DJMiningRules() {
    }

    public static boolean isMiningIntent(ItemStack stack, BlockState state) {
        return stack != null && !stack.isEmpty() && state != null && !state.isAir()
                && stack.isCorrectToolForDrops(state);
    }
}

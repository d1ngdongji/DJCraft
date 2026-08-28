package otto.djgun.djcraft.client.animation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

/** Classifies inventory changes that should bypass DJ first-person switch transitions. */
public final class DJAnimationSwitchRules {
    private DJAnimationSwitchRules() {
    }

    public static boolean isInstantRemoval(Player player, ItemStack outgoingStack, ItemStack incomingStack) {
        return player != null
                && outgoingStack != null
                && incomingStack != null
                && incomingStack.isEmpty()
                && DJItemBehaviorManager.is(outgoingStack, DJItemBehavior.TRIDENT)
                && player.getCooldowns().isOnCooldown(outgoingStack.getItem());
    }

    public static boolean isHandSwap(ItemStack sourceMain, ItemStack sourceOff,
            ItemStack targetMain, ItemStack targetOff) {
        if (sourceMain == null || sourceOff == null || targetMain == null || targetOff == null) {
            return false;
        }
        return DJHandSwapTransaction.isSwap(
                sourceMain, sourceOff, targetMain, targetOff, ItemStack::matches);
    }
}

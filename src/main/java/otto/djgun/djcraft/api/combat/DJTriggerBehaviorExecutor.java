package otto.djgun.djcraft.api.combat;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/** Executes a registered trigger-family behavior on the authoritative server thread. */
@FunctionalInterface
public interface DJTriggerBehaviorExecutor {
    InteractionResultHolder<ItemStack> execute(DJTriggerBehaviorContext context);
}

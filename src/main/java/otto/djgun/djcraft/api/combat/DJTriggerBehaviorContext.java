package otto.djgun.djcraft.api.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.combat.HitResult;

/** Server-only execution context provided after DJ proof, source, cooldown and energy validation. */
public record DJTriggerBehaviorContext(
        ServerPlayer player,
        InteractionHand hand,
        ItemStack sourceStack,
        HitResult judgment,
        long actionSequence) {
    public DJTriggerBehaviorContext {
        if (player == null || hand == null || sourceStack == null || sourceStack.isEmpty() || judgment == null) {
            throw new IllegalArgumentException("Invalid DJ trigger behavior context");
        }
    }
}

package otto.djgun.djcraft.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.entity.DJThrownMace;

public final class DJMaceCombatService {
    private DJMaceCombatService() {
    }

    public static void throwMace(ServerPlayer player, DJActionContext action) {
        InteractionHand hand = action.hand();
        ItemStack stack = action.resolveLiveStack(player);
        if (stack.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DJCombatHandler.beginProjectileFire(player, action);
        try {
            DJThrownMace mace = new DJThrownMace(player, level);
            mace.setItem(action.stackSnapshot());
            mace.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                    DJMaceThrowRules.INITIAL_SPEED, 1.0F);
            level.addFreshEntity(mace);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            player.swing(hand, true);
        } finally {
            DJCombatHandler.endProjectileFire(player.getUUID());
        }
    }
}

package otto.djgun.djcraft.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;
import otto.djgun.djcraft.session.DJModeManager;

public final class DJTridentCombatService {
    private DJTridentCombatService() {
    }

    public static void fire(ServerPlayer player, DJActionContext action) {
        InteractionHand hand = action.hand();
        ItemStack stack = action.resolveLiveStack(player);
        if (stack.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DJCombatHandler.beginProjectileFire(player, action);
        try {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            ThrownTrident trident = new ThrownTrident(level, player, stack);
            long returnAtTimelineMs = DJModeManager.getInstance().getSession(player)
                    .map(session -> session.getCompletionTimeMs(DJTridentRules.RETURN_DELAY_BEATS))
                    .orElse(action.result().judgedAtMs() + 1500L);
            ((DJThrownTridentExtension) trident).djcraft$configureFlight(
                    action.sessionId(), returnAtTimelineMs);
            trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                    TridentItem.SHOOT_POWER, 1.0F);
            if (player.hasInfiniteMaterials()) {
                trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            level.addFreshEntity(trident);
            var sound = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                    .orElse(SoundEvents.TRIDENT_THROW);
            level.playSound(null, trident, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.hasInfiniteMaterials()) {
                player.getInventory().removeItem(stack);
            }
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            player.swing(hand, true);
        } finally {
            DJCombatHandler.endProjectileFire(player.getUUID());
        }
    }
}

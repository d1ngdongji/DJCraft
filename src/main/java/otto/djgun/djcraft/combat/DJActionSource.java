package otto.djgun.djcraft.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server-verifiable inventory location captured when a DJ action begins. */
public record DJActionSource(int slot, int itemRegistryId) {
    public static final int OFFHAND_SLOT = 40;
    public static final StreamCodec<FriendlyByteBuf, DJActionSource> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DJActionSource::slot,
            ByteBufCodecs.VAR_INT, DJActionSource::itemRegistryId,
            DJActionSource::new);

    public static DJActionSource capture(Player player, InteractionHand hand, ItemStack actionStack) {
        int slot = hand == InteractionHand.OFF_HAND
                ? OFFHAND_SLOT
                : findHotbarSlot(player, actionStack);
        return new DJActionSource(slot, BuiltInRegistries.ITEM.getId(actionStack.getItem()));
    }

    public ItemStack resolve(Player player, InteractionHand hand) {
        if (!isValidFor(hand)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        Item claimedItem = BuiltInRegistries.ITEM.byId(itemRegistryId);
        return claimedItem != null && stack.getItem() == claimedItem ? stack : ItemStack.EMPTY;
    }

    /** Validates that the claimed source is authoritative when a new action transaction is opened. */
    public ItemStack resolveAtActionStart(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = resolve(player, hand);
        if (stack.isEmpty() || hand == InteractionHand.OFF_HAND) {
            return stack;
        }
        if (player.getInventory().selected == slot
                || DJActionSourceHistory.wasRecentlySelected(player, slot)) {
            return stack;
        }
        return player.isUsingItem()
                && player.getUsedItemHand() == hand
                && player.getUseItem() == stack
                ? stack
                : ItemStack.EMPTY;
    }

    public void replace(ServerPlayer player, InteractionHand hand, ItemStack replacement) {
        if (isValidFor(hand)) {
            player.getInventory().setItem(slot, replacement);
        }
    }

    public boolean isValidFor(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? slot == OFFHAND_SLOT : slot >= 0 && slot < 9;
    }

    private static int findHotbarSlot(Player player, ItemStack actionStack) {
        int selected = player.getInventory().selected;
        if (player.getInventory().getItem(selected) == actionStack) {
            return selected;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot) == actionStack) {
                return slot;
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(slot), actionStack)) {
                return slot;
            }
        }
        return selected;
    }
}

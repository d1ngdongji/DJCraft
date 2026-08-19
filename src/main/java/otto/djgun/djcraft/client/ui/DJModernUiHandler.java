package otto.djgun.djcraft.client.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.util.DJClientUiBridge;

@OnlyIn(Dist.CLIENT)
public final class DJModernUiHandler implements DJClientUiBridge.Handler {
    public static final DJModernUiHandler INSTANCE = new DJModernUiHandler();

    private DJModernUiHandler() {
    }

    @Override
    public void openPlayer(ItemStack jukeboxStack, InteractionHand hand) {
        if (!isModernUiLoaded()) {
            DJCraft.LOGGER.warn("ModernUI is not loaded. Cannot open DJ Player UI.");
            return;
        }

        try {
            DJPlayerUIHelper.openPlayerUI(jukeboxStack, findInventorySlot(jukeboxStack));
        } catch (RuntimeException | LinkageError error) {
            DJCraft.LOGGER.error("Failed to open DJ Player UI", error);
        }
    }

    private static int findInventorySlot(ItemStack target) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return -1;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == target) {
                return slot;
            }
        }
        return handFallbackSlot(player, target);
    }

    private static int handFallbackSlot(net.minecraft.world.entity.player.Player player, ItemStack target) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(slot), target)) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void openCrafting(InteractionHand hand, BlockPos tablePos) {
        if (!isModernUiLoaded()) {
            DJCraft.LOGGER.warn("ModernUI is not loaded. Cannot open DJ Crafting UI.");
            return;
        }

        try {
            DJCraftingUIHelper.openCraftingUI(hand, tablePos);
        } catch (RuntimeException | LinkageError error) {
            DJCraft.LOGGER.error("Failed to open DJ Crafting UI", error);
        }
    }

    private static boolean isModernUiLoaded() {
        return ModList.get().isLoaded("modernui");
    }
}

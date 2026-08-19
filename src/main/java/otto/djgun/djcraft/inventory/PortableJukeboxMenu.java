package otto.djgun.djcraft.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.init.ModItems;

import javax.annotation.Nonnull;

public class PortableJukeboxMenu extends AbstractContainerMenu {

    private static final int JUKEBOX_ROWS = 6;
    private static final int JUKEBOX_COLS = 9;
    private static final int JUKEBOX_SLOTS = JUKEBOX_ROWS * JUKEBOX_COLS; // 54

    public PortableJukeboxMenu(int containerId, @Nonnull Inventory playerInventory,
            @Nonnull PortableJukeboxContainer container) {
        super(MenuType.GENERIC_9x6, containerId);
        checkContainerSize(container, JUKEBOX_SLOTS);

        // Add jukebox slots with item restriction
        for (int row = 0; row < JUKEBOX_ROWS; row++) {
            for (int col = 0; col < JUKEBOX_COLS; col++) {
                final int slotIndex = col + row * JUKEBOX_COLS;
                this.addSlot(new Slot(container, slotIndex, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(@Nonnull ItemStack stack) {
                        return otto.djgun.djcraft.item.EmptyDiscItem.isRecordedAndLoaded(stack);
                    }
                });
            }
        }

        // Add player inventory slots (rows 0-2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Add player hotbar slots (row 3)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < JUKEBOX_SLOTS) {
                // Moving from jukebox to player inventory
                if (!this.moveItemStackTo(slotStack, JUKEBOX_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to jukebox (only if it's an empty_disc)
                if (otto.djgun.djcraft.item.EmptyDiscItem.isRecordedAndLoaded(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, 0, JUKEBOX_SLOTS, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Not allowed – do nothing
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return true;
    }
}

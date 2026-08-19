package otto.djgun.djcraft.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.init.ModItems;

public class PortableJukeboxContainer extends SimpleContainer {
    private final ItemStack owner;

    public PortableJukeboxContainer(ItemStack jukeboxStack) {
        super(54);
        this.owner = jukeboxStack;
        net.minecraft.world.item.component.ItemContainerContents contents = jukeboxStack.getOrDefault(
                net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.EMPTY);
        contents.copyInto(this.getItems());
    }

    public void saveContents() {
        owner.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return otto.djgun.djcraft.item.EmptyDiscItem.isRecordedAndLoaded(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        saveContents();
    }
}

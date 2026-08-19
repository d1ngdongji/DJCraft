package otto.djgun.djcraft.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import otto.djgun.djcraft.util.DJClientUiBridge;

public class PortableJukeboxItem extends Item {
    public PortableJukeboxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                otto.djgun.djcraft.inventory.PortableJukeboxContainer jukeboxContainer = new otto.djgun.djcraft.inventory.PortableJukeboxContainer(
                        itemStack);
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, playerInventory, playerEntity) -> new otto.djgun.djcraft.inventory.PortableJukeboxMenu(id,
                                playerInventory, jukeboxContainer),
                        net.minecraft.network.chat.Component.translatable("container.djcraft.portable_jukebox")));
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }

        if (level.isClientSide()) {
            DJClientUiBridge.openPlayer(itemStack, usedHand);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}

package otto.djgun.djcraft.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Base item for passive effects that are active from any hotbar slot or the
 * offhand.
 */
public class HotbarOrOffhandEffectItem extends Item {
    private static final int HOTBAR_SIZE = 9;

    public HotbarOrOffhandEffectItem(Properties properties) {
        super(properties);
    }

    public final boolean isActiveFor(Player player) {
        if (player.getOffhandItem().is(this)) {
            return true;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (player.getInventory().getItem(slot).is(this)) {
                return true;
            }
        }
        return false;
    }
}

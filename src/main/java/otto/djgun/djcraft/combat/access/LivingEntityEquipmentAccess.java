package otto.djgun.djcraft.combat.access;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public interface LivingEntityEquipmentAccess {
    ItemStack djcraft$getLastHandItem(EquipmentSlot slot);
}

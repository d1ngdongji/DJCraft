package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import otto.djgun.djcraft.combat.access.LivingEntityEquipmentAccess;

@Mixin(LivingEntity.class)
public interface LivingEntityEquipmentAccessMixin extends LivingEntityEquipmentAccess {
    @Override
    @Invoker("getLastHandItem")
    ItemStack djcraft$getLastHandItem(EquipmentSlot slot);
}

package otto.djgun.djcraft.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface DJWeaponSoundIdentityResolver {
    ResourceLocation resolve(ItemStack stack);
}

package otto.djgun.djcraft.init;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import otto.djgun.djcraft.DJCraft;

public final class ModEnchantments {
    public static final ResourceKey<Enchantment> AERIAL_STEP = key("aerial_step");
    public static final ResourceKey<Enchantment> RENDING = key("rending");
    public static final ResourceKey<Enchantment> RAY_OVERCHARGE = key("ray_overcharge");
    public static final ResourceKey<Enchantment> LINGERING_SWEEP = key("lingering_sweep");

    private ModEnchantments() {
    }

    public static int level(RegistryAccess registries, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return registries.registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(enchantment)
                .map(stack::getEnchantmentLevel)
                .orElse(0);
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, path));
    }
}

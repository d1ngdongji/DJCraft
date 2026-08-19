package otto.djgun.djcraft.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import otto.djgun.djcraft.DJCraft;

public final class ModItemTags {
    public static final TagKey<Item> SWIFT = create("swift");
    public static final TagKey<Item> SMASH = create("smash");

    private ModItemTags() {
    }

    private static TagKey<Item> create(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, path));
    }
}

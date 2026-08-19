package otto.djgun.djcraft.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DJCraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DJCRAFT =
            CREATIVE_MODE_TABS.register("djcraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.djcraft"))
                    .icon(() -> new ItemStack(ModItems.EMPTY_DISC.get()))
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries()
                            .forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeModeTabs() {
    }
}

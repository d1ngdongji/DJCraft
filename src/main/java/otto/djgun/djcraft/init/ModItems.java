package otto.djgun.djcraft.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.item.BandOfEnergyItem;
import otto.djgun.djcraft.item.DJFumoItem;
import otto.djgun.djcraft.item.EmptyDiscItem;
import otto.djgun.djcraft.item.HotbarOrOffhandEffectItem;
import otto.djgun.djcraft.item.NoteInABottleItem;

public class ModItems {
        public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DJCraft.MODID);

        public static final DeferredItem<Item> EMPTY_DISC = ITEMS.registerItem("empty_disc", EmptyDiscItem::new,
                        new Item.Properties().stacksTo(1));

        public static final DeferredItem<DJFumoItem> DJFUMO = ITEMS.registerItem("djfumo", DJFumoItem::new,
                        new Item.Properties().stacksTo(1));

        public static final DeferredItem<HotbarOrOffhandEffectItem> FLOWERY = ITEMS.registerItem("flowery",
                        HotbarOrOffhandEffectItem::new,
                        new Item.Properties().stacksTo(1));

        public static final DeferredItem<NoteInABottleItem> NOTE_IN_A_BOTTLE = ITEMS.registerItem("note_in_a_bottle",
                        NoteInABottleItem::new, new Item.Properties().stacksTo(1));

        public static final DeferredItem<BandOfEnergyItem> BAND_OF_ENERGY = ITEMS.registerItem("band_of_energy",
                        BandOfEnergyItem::new, new Item.Properties().stacksTo(1));

        public static final DeferredItem<CrossbowItem> LASER_CROSSBOW = ITEMS.registerItem("laser_crossbow",
                        CrossbowItem::new, rayCrossbowProperties());

        public static final DeferredItem<CrossbowItem> MAGIC_CROSSBOW = ITEMS.registerItem("magic_crossbow",
                        CrossbowItem::new, rayCrossbowProperties());

        public static final DeferredItem<BowItem> EXPLOSIVE_BOW = ITEMS.registerItem("explosive_bow",
                        BowItem::new, new Item.Properties().stacksTo(1).durability(384));

        public static final DeferredItem<Item> PORTABLE_JUKEBOX = ITEMS.registerItem("portable_jukebox",
                        otto.djgun.djcraft.item.PortableJukeboxItem::new,
                        new Item.Properties().stacksTo(1).component(
                                        net.minecraft.core.component.DataComponents.CONTAINER,
                                        net.minecraft.world.item.component.ItemContainerContents.EMPTY));

        public static final DeferredItem<Item> DJ_CRAFTING_TABLE = ITEMS.registerItem("dj_crafting_table",
                        properties -> new BlockItem(ModBlocks.DJ_CRAFTING_TABLE.get(), properties),
                        new Item.Properties());

        private static Item.Properties rayCrossbowProperties() {
                return new Item.Properties().stacksTo(1).durability(465)
                                .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        }
}

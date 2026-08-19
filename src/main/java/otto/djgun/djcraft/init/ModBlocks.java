package otto.djgun.djcraft.init;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.block.DJCraftingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DJCraft.MODID);

    public static final DeferredBlock<Block> DJ_CRAFTING_TABLE = BLOCKS.registerBlock("dj_crafting_table",
            DJCraftingBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(net.minecraft.world.level.block.SoundType.WOOD));
}

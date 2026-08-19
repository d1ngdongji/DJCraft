package otto.djgun.djcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.util.DJClientUiBridge;

public class DJCraftingBlock extends Block {

    public DJCraftingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        // Player right clicks with main hand usually, check if holding empty disc
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean isMainHandDisc = isBlankDisc(stack);
        InteractionHand validHand = InteractionHand.MAIN_HAND;

        if (!isMainHandDisc) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (isBlankDisc(stack)) {
                validHand = InteractionHand.OFF_HAND;
            } else {
                // The workbench also hosts the Cyber Grind page, so it remains usable
                // without a blank disc. Crafting itself is still server-validated.
                validHand = InteractionHand.MAIN_HAND;
            }
        }

        if (level.isClientSide) {
            DJClientUiBridge.openCrafting(validHand, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (isBlankDisc(stack)) {
            if (level.isClientSide) {
                DJClientUiBridge.openCrafting(hand, pos);
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            DJClientUiBridge.openCrafting(hand, pos);
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    private static boolean isBlankDisc(ItemStack stack) {
        return stack.is(ModItems.EMPTY_DISC.get()) && stack.get(ModDataComponents.TRACK_PACK_ID.get()) == null;
    }
}

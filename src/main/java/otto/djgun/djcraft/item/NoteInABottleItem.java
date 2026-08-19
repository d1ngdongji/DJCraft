package otto.djgun.djcraft.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class NoteInABottleItem extends HotbarOrOffhandEffectItem {
    public static final int AIR_JUMP_BONUS = 1;

    public NoteInABottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.djcraft.note_in_a_bottle")
                .withStyle(ChatFormatting.GRAY));
    }
}

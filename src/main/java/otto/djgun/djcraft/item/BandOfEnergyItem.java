package otto.djgun.djcraft.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class BandOfEnergyItem extends HotbarOrOffhandEffectItem {
    public static final double MAX_ENERGY_BONUS = 25.0;

    public BandOfEnergyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.djcraft.band_of_energy",
                (int) MAX_ENERGY_BONUS).withStyle(ChatFormatting.GRAY));
    }
}

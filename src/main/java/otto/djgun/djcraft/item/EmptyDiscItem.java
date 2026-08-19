package otto.djgun.djcraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.data.DiscStatistics;

import java.util.List;
import java.util.Locale;

public class EmptyDiscItem extends Item {
    public EmptyDiscItem(Properties properties) {
        super(properties);
    }

    public static boolean isRecordedAndLoaded(ItemStack stack) {
        if (!stack.is(otto.djgun.djcraft.init.ModItems.EMPTY_DISC.get())) {
            return false;
        }
        String packId = stack.get(ModDataComponents.TRACK_PACK_ID.get());
        return packId != null && !packId.isBlank()
                && otto.djgun.djcraft.loader.TrackPackManager.getInstance().isPackLoaded(packId);
    }

    @Override
    public Component getName(ItemStack stack) {
        String packId = stack.get(ModDataComponents.TRACK_PACK_ID.get());
        if (packId != null) {
            var packOpt = otto.djgun.djcraft.loader.TrackPackManager.getInstance().getTrackPack(packId);
            if (packOpt.isPresent()) {
                String displayName = packOpt.get().meta().displayName();
                if (displayName != null && !displayName.isEmpty()) {
                    return Component.literal(displayName);
                } else {
                    return Component.literal(packId);
                }
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.get(ModDataComponents.TRACK_PACK_ID.get()) == null) {
            return;
        }
        DiscStatistics statistics = stack.getOrDefault(
                ModDataComponents.DISC_STATISTICS.get(), DiscStatistics.EMPTY);
        tooltipComponents.add(Component.translatable(
                "tooltip.djcraft.disc_max_combo", statistics.maxCombo()));
        tooltipComponents.add(Component.translatable(
                "tooltip.djcraft.disc_play_time", formatDuration(statistics.totalPlayTimeMs())));
    }

    private static String formatDuration(long totalMs) {
        long totalSeconds = Math.max(0L, totalMs) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}

package otto.djgun.djcraft.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;

/** Advancement-style notification shown when local track playback starts. */
@OnlyIn(Dist.CLIENT)
public final class DJTrackToast implements Toast {
    private static final ResourceLocation BACKGROUND_SPRITE =
            ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME_MS = 5_000L;
    private static final long TITLE_TIME_MS = 1_500L;
    private static final float FADE_TIME_MS = 300.0F;

    private final Component displayName;
    private final ItemStack disc;

    private DJTrackToast(TrackPack pack) {
        String configuredName = pack.meta() == null ? null : pack.meta().displayName();
        this.displayName = Component.literal(configuredName == null || configuredName.isBlank()
                ? pack.id()
                : configuredName);
        this.disc = new ItemStack(ModItems.EMPTY_DISC.get());
        this.disc.set(ModDataComponents.TRACK_PACK_ID.get(), pack.id());
    }

    public static void show(String trackPackId) {
        TrackPackManager.getInstance().getTrackPack(trackPackId).ifPresent(pack ->
                Minecraft.getInstance().getToasts().addToast(new DJTrackToast(pack)));
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long visibleTimeMs) {
        var font = toastComponent.getMinecraft().font;
        List<FormattedCharSequence> nameLines = font.split(displayName, 125);
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());

        if (nameLines.size() <= 1) {
            guiGraphics.drawString(font, Component.translatable("toast.djcraft.now_playing"),
                    30, 7, 0xFFFFFF00, false);
            FormattedCharSequence line = nameLines.isEmpty()
                    ? displayName.getVisualOrderText()
                    : nameLines.getFirst();
            guiGraphics.drawString(font, line,
                    30, 18, 0xFFFFFFFF, false);
        } else if (visibleTimeMs < TITLE_TIME_MS) {
            int alpha = Mth.floor(Mth.clamp((TITLE_TIME_MS - visibleTimeMs) / FADE_TIME_MS,
                    0.0F, 1.0F) * 255.0F) << 24 | 0x04000000;
            guiGraphics.drawString(font, Component.translatable("toast.djcraft.now_playing"),
                    30, 11, 0x00FFFF00 | alpha, false);
        } else {
            int alpha = Mth.floor(Mth.clamp((visibleTimeMs - TITLE_TIME_MS) / FADE_TIME_MS,
                    0.0F, 1.0F) * 252.0F) << 24 | 0x04000000;
            int y = height() / 2 - nameLines.size() * 9 / 2;
            for (FormattedCharSequence line : nameLines) {
                guiGraphics.drawString(font, line, 30, y, 0x00FFFFFF | alpha, false);
                y += 9;
            }
        }

        guiGraphics.renderFakeItem(disc, 8, 8);
        return visibleTimeMs >= DISPLAY_TIME_MS * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE
                : Visibility.SHOW;
    }
}

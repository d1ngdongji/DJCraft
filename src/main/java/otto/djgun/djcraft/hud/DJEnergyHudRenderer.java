package otto.djgun.djcraft.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.session.DJModeManagerClient;

import java.util.Locale;

/** Renders the DJ session energy immediately above the vanilla hunger bar. */
@OnlyIn(Dist.CLIENT)
public final class DJEnergyHudRenderer {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "textures/gui/energy/frame.png");
    private static final ResourceLocation FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "textures/gui/energy/fill.png");
    private static final int BAR_WIDTH = 64;
    private static final int BAR_HEIGHT = 10;
    private static final int HUNGER_RIGHT_OFFSET = 91;
    private static final int ABOVE_HUNGER_OFFSET = 52;
    private static final int TEXT_GAP = 3;
    private static final float TEXT_SCALE = 0.75f;

    private DJEnergyHudRenderer() {
    }

    public static void render(GuiGraphics gui) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int barX = screenWidth / 2 + HUNGER_RIGHT_OFFSET - BAR_WIDTH;
        int barY = screenHeight - ABOVE_HUNGER_OFFSET;

        gui.blit(FRAME_TEXTURE, barX, barY, 0.0f, 0.0f,
                BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);

        double maxEnergy = sanitize(session.getMaxEnergy());
        double energy = Math.clamp(sanitize(session.getEnergy()), 0.0, maxEnergy);
        int fillWidth = maxEnergy > 0.0
                ? Math.clamp((int) Math.round(BAR_WIDTH * energy / maxEnergy), 0, BAR_WIDTH)
                : 0;
        if (fillWidth > 0) {
            gui.blit(FILL_TEXTURE, barX, barY, 0.0f, 0.0f,
                    fillWidth, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        }

        renderValue(gui, minecraft.font, formatValue(energy) + "/" + formatValue(maxEnergy), barX, barY);
    }

    private static void renderValue(GuiGraphics gui, Font font, String value, int barX, int barY) {
        float textRight = barX - TEXT_GAP;
        float textY = barY + (BAR_HEIGHT - font.lineHeight * TEXT_SCALE) / 2.0f;

        gui.pose().pushPose();
        gui.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        gui.drawString(font, value,
                Math.round(textRight / TEXT_SCALE - font.width(value)),
                Math.round(textY / TEXT_SCALE),
                0xFFFFFFFF,
                true);
        gui.pose().popPose();
    }

    private static String formatValue(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.001) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}

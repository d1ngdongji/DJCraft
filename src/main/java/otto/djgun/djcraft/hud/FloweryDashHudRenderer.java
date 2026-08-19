package otto.djgun.djcraft.hud;

import net.minecraft.client.gui.GuiGraphics;
import otto.djgun.djcraft.client.render.DJRainbowColor;
import otto.djgun.djcraft.client.render.FloweryDashVisualState;

/** Renders the short-lived rainbow window border for a Flowery dash. */
public final class FloweryDashHudRenderer {
    private static final int BORDER_WIDTH = 6;
    private FloweryDashHudRenderer() {
    }

    public static void render(GuiGraphics gui) {
        if (!FloweryDashVisualState.isLocalActive()) {
            return;
        }

        DJRainbowColor.Rgb rainbow =
                FloweryDashVisualState.sampleRainbow(System.nanoTime() / 1_000_000L);
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        for (int inset = 0; inset < BORDER_WIDTH; inset++) {
            int alpha = 190 - inset * 22;
            int color = argb(alpha, rainbow);
            gui.fill(inset, inset, width - inset, inset + 1, color);
            gui.fill(inset, height - inset - 1, width - inset, height - inset, color);
            gui.fill(inset, inset + 1, inset + 1, height - inset - 1, color);
            gui.fill(width - inset - 1, inset + 1, width - inset, height - inset - 1, color);
        }
    }

    private static int argb(int alpha, DJRainbowColor.Rgb color) {
        int red = Math.round(color.red() * 255.0F);
        int green = Math.round(color.green() * 255.0F);
        int blue = Math.round(color.blue() * 255.0F);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}

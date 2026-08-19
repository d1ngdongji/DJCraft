package otto.djgun.djcraft.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import otto.djgun.djcraft.client.CyberGrindClientState;

public final class CyberGrindHudRenderer {
    private CyberGrindHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        var state = CyberGrindClientState.getInstance().state();
        if (!state.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Component title = state.countdownTicks() > 0
                ? Component.translatable("hud.djcraft.cyber_grind.countdown",
                        Math.max(1, (state.countdownTicks() + 19) / 20))
                : Component.translatable("hud.djcraft.cyber_grind.wave",
                        state.wave(), state.livingWeight(), state.advanceThreshold());
        int width = minecraft.font.width(title);
        int x = (graphics.guiWidth() - width) / 2;
        graphics.fill(x - 8, 7, x + width + 8, 23, 0xA0101018);
        graphics.drawString(minecraft.font, title, x, 11, 0x66FFFF, true);
        Component completed = Component.translatable("hud.djcraft.cyber_grind.completed",
                state.completedWaves());
        graphics.drawCenteredString(minecraft.font, completed, graphics.guiWidth() / 2, 27,
                0xFFE6A8);
    }
}

package otto.djgun.djcraft.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Renders combo-protection chances beneath the vanilla boss-bar stack. */
@OnlyIn(Dist.CLIENT)
public final class DJToleranceHudRenderer {
    private static final ResourceLocation AVAILABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "textures/gui/tolerance/heart.png");
    private static final ResourceLocation EMPTY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "textures/gui/tolerance/heart_empty.png");
    private static final int SOURCE_SIZE = 16;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int DEFAULT_Y = 33;
    private static final int BOSS_STACK_GAP = 2;
    private static int renderY = DEFAULT_Y;

    private DJToleranceHudRenderer() {
    }

    public static void beginBossOverlay() {
        renderY = DEFAULT_Y;
    }

    public static void observeBossEvent(CustomizeGuiOverlayEvent.BossEventProgress event) {
        renderY = Math.max(renderY, event.getY() + event.getIncrement() + BOSS_STACK_GAP);
    }

    public static void render(GuiGraphics gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null || session.getMaxToleranceChances() <= 0) {
            return;
        }

        int maxChances = session.getMaxToleranceChances();
        int availableChances = Math.clamp(session.getToleranceChances(), 0, maxChances);
        int totalWidth = maxChances * ICON_SIZE + (maxChances - 1) * ICON_GAP;
        int startX = (gui.guiWidth() - totalWidth) / 2;

        for (int index = 0; index < maxChances; index++) {
            boolean available = index < availableChances;
            gui.blit(available ? AVAILABLE_TEXTURE : EMPTY_TEXTURE,
                    startX + index * (ICON_SIZE + ICON_GAP), renderY,
                    ICON_SIZE, ICON_SIZE, 0.0f, 0.0f,
                    SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE);
        }
    }
}

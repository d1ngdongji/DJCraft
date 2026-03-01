package otto.djgun.djcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.sound.OpenALHelper;

import java.util.Optional;

/**
 * DJ调试HUD
 * 在屏幕上显示当前播放时间和节拍信息
 * 现在使用 OpenAL 高精度时间
 */
@OnlyIn(Dist.CLIENT)
public class DJDebugHud {

    private static boolean enabled = true;

    /**
     * 渲染Debug HUD
     */
    public static void render(GuiGraphics guiGraphics) {
        if (!enabled || !Config.ENABLE_DEBUG_HUD.get())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // 获取客户端的DJ会话
        Optional<DJSessionClient> sessionOpt = DJModeManagerClient.getInstance().getSession();
        if (sessionOpt.isEmpty())
            return;

        DJSessionClient session = sessionOpt.get();
        if (!session.isPlaying())
            return;

        Font font = mc.font;
        int x = 10;
        int y = 10;
        int lineHeight = 12;
        int color = 0xFFFFFF;
        int headerColor = 0x00FF00;
        int beatColor = 0xFFAA00;
        int openALColor = 0x00FFFF;

        // 标题
        guiGraphics.drawString(font, "§a♪ DJ Debug", x, y, headerColor);
        y += lineHeight + 4;

        // 当前时间
        long currentMs = session.getCurrentTimeMs();
        guiGraphics.drawString(font, String.format("Time: %dms", currentMs), x, y, color);
        y += lineHeight;

        // 总时长
        int totalMs = session.getTrackPack().getTotalDurationMs();
        float progress = (float) currentMs / totalMs * 100;
        guiGraphics.drawString(font, String.format("Progress: %.1f%%", progress), x, y, color);
        y += lineHeight;

        // 上一节拍
        BeatEvent prevBeat = session.getPreviousBeat();
        if (prevBeat != null) {
            long sincePrev = session.getMsSincePreviousBeat();
            guiGraphics.drawString(font, String.format("Prev: %dms (-%dms)", prevBeat.t(), sincePrev), x, y, beatColor);
        } else {
            guiGraphics.drawString(font, "Prev: ---", x, y, 0x888888);
        }
        y += lineHeight;

        // 下一节拍
        BeatEvent nextBeat = session.getNextBeat();
        if (nextBeat != null) {
            long toNext = session.getMsToNextBeat();
            guiGraphics.drawString(font, String.format("Next: %dms (+%dms)", nextBeat.t(), toNext), x, y, beatColor);
        } else {
            guiGraphics.drawString(font, "Next: ---", x, y, 0x888888);
        }
        y += lineHeight;

        // 节拍进度
        int triggered = session.getTriggeredBeatCount();
        int total = session.getTotalBeatCount();
        guiGraphics.drawString(font, String.format("Beats: %d/%d", triggered, total), x, y, color);
        y += lineHeight;

        // BPM
        int bpm = session.getTrackPack().getBpm();
        guiGraphics.drawString(font, String.format("BPM: %d", bpm), x, y, color);
        y += lineHeight + 4;

        // OpenAL 信息
        guiGraphics.drawString(font, "§b♪ OpenAL", x, y, openALColor);
        y += lineHeight;

        // OpenAL source ID
        int sourceId = session.getOpenALSourceId();
        guiGraphics.drawString(font, String.format("Source ID: %d", sourceId), x, y, openALColor);
        y += lineHeight;

        // OpenAL 时间模式
        String timeMode = session.isUsingOpenALTime() ? "§aOpenAL" : "§cSystem";
        guiGraphics.drawString(font, "Time Mode: " + timeMode, x, y, color);
        y += lineHeight;

        // OpenAL 状态
        String status;
        if (OpenALHelper.isDJSourcePlaying()) {
            status = "§aPlaying";
        } else if (OpenALHelper.isDJSourcePaused()) {
            status = "§ePaused";
        } else {
            status = "§7Stopped";
        }
        guiGraphics.drawString(font, "AL Status: " + status, x, y, color);
    }

    /**
     * 开启/关闭Debug HUD
     */
    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * 设置是否开启Debug HUD
     */
    public static void setEnabled(boolean enabled) {
        DJDebugHud.enabled = enabled;
    }

    /**
     * 是否开启Debug HUD
     */
    public static boolean isEnabled() {
        return enabled;
    }
}

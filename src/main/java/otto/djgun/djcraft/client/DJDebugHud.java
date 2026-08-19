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
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;

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

        // 有效裁剪区间内的播放进度
        int playbackStartMs = session.getTrackPack().getPlaybackStartMs();
        int playbackEndMs = session.getTrackPack().getTotalDurationMs();
        long playbackPositionMs = session.getPlaybackTimeMs();
        float progress = (float) (playbackPositionMs - playbackStartMs)
                / (playbackEndMs - playbackStartMs) * 100;
        progress = Math.clamp(progress, 0.0f, 100.0f);
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

        guiGraphics.drawString(font, String.format("Combo: %d", session.getCombo()), x, y, beatColor);
        y += lineHeight;

        guiGraphics.drawString(font, String.format("Energy: %.1f/%.1f",
                session.getEnergy(), session.getMaxEnergy()), x, y, 0x55FFFF);
        y += lineHeight;

        // BPM
        int bpm = session.getTrackPack().getBpm();
        guiGraphics.drawString(font, String.format("BPM: %d", bpm), x, y, color);
        y += lineHeight + 4;

        DJAnimationRuntime animationRuntime = DJAnimationRuntime.getInstance();
        DJAnimationEvent activeTransition = animationRuntime.activeTransition();
        DJAnimationEvent animationEvent = activeTransition != null
                ? activeTransition
                : animationRuntime.lastEvent();
        var animationClock = animationRuntime.lastSnapshot();
        guiGraphics.drawString(font, "§dAnimation", x, y, 0xFF55FF);
        y += lineHeight;
        if (animationClock != null) {
            guiGraphics.drawString(font, String.format("VBeat: %.3f Gen: %d",
                    animationClock.virtualBeat(), animationClock.timelineGeneration()), x, y, color);
            y += lineHeight;
        }
        if (animationEvent != null) {
            double markerDelta = animationClock == null ? 0.0
                    : animationClock.virtualBeat() - animationEvent.virtualBeat();
            guiGraphics.drawString(font, String.format("#%d %s %s Δ%.3f",
                    animationEvent.sequence(), animationEvent.hand(), animationEvent.semantic().id(), markerDelta),
                    x, y, color);
            y += lineHeight + 4;
        }

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

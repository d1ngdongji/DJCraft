package otto.djgun.djcraft.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackSettings;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DJ 模式准星判定线渲染器
 *
 * 纹理路径：assets/djcraft/textures/crosshair/vshape.png（32×32，透明背景+白色轮廓）
 */
@OnlyIn(Dist.CLIENT)
public class DJCrosshairRenderer {

    private static final ResourceLocation VSHAPE_TEXTURE = ResourceLocation.fromNamespaceAndPath("djcraft",
            "textures/crosshair/vshape.png");
    private static final ResourceLocation CROSSHAIR_DOT = ResourceLocation.fromNamespaceAndPath("djcraft",
            "textures/crosshair/dot.png");
    private static final ResourceLocation CROSSHAIR_H = ResourceLocation.fromNamespaceAndPath("djcraft",
            "textures/crosshair/horizontal_line.png");
    private static final ResourceLocation CROSSHAIR_V = ResourceLocation.fromNamespaceAndPath("djcraft",
            "textures/crosshair/vertical_line.png");

    /** 贴图实际像素尺寸 */
    private static final int TEX_W = 32;
    private static final int TEX_H = 32;

    /** 判定线在屏幕高度方向的比例 (0=顶, 1=底) */
    private static final float JUDGE_Y_RATIO = 0.48f;

    /** 判定线尺寸倍率（节拍到达时与此相同），缩小 30% 后为 1.12f */
    private static final float JUDGE_SCALE = 1.12f;

    /** 节拍最远时的最小缩放（从小变大效果的起点） */
    private static final float MIN_SCALE = 0.175f;

    /** 准星判定线的最远物理距离上限 (像素) */
    private static final float MAX_SPAWN_DIST_Y = 140f;

    /** 节拍过时后多少毫秒自然淡出（过线一点即消） */
    private static final long FADE_OUT_MS = 120;

    /** 玩家攻击后节拍冻结并消散的时间（毫秒） */
    private static final long DISMISS_DURATION_MS = 180;

    /** 判定反馈持续时间（毫秒） */
    private static final long FEEDBACK_DURATION_MS = 380;

    // ----- 反馈状态 -----
    private static long feedbackStartMs = -1;
    private static boolean feedbackIsHit = false;
    private static long crosshairAnimStartMs = -1;

    /**
     * 玩家攻击后被"解除"的节拍，记录其冻结状态
     * key = beat.t()，value = [wallTime解除时刻, sessionTime解除时刻, isHit(1或0)]
     */
    private static final Map<Long, long[]> dismissedBeats = new LinkedHashMap<>();

    /**
     * 通知渲染器玩家攻击了，携带被判定的节拍事件（可为 null表示 miss）
     */
    public static void notifyJudgment(boolean isHit, @Nullable BeatEvent judgedBeat, long sessionTimeMs) {
        feedbackStartMs = System.currentTimeMillis();
        feedbackIsHit = isHit;
        crosshairAnimStartMs = feedbackStartMs;
        if (judgedBeat != null) {
            dismissedBeats.put((long) judgedBeat.t(),
                    new long[] { System.currentTimeMillis(), sessionTimeMs, isHit ? 1L : 0L });
        }
    }

    public static void renderCenterCrosshair(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;

        float animOffset = 0;
        if (crosshairAnimStartMs >= 0) {
            long elapsed = System.currentTimeMillis() - crosshairAnimStartMs;
            if (elapsed < 250) {
                float t = (float) elapsed / 250.0f;
                // 缓动动画效果：向外弹出最大6像素，然后收回
                animOffset = 6.0f * (1.0f - t) * (1.0f - t);
            }
        }

        float gap = 2.0f + animOffset;

        gui.pose().pushPose();
        gui.pose().translate(cx, cy, 0);
        // 缩小准星整体尺寸
        gui.pose().scale(0.65f, 0.65f, 1.0f);

        // dot: 2x2
        renderTextureWithColor(gui, CROSSHAIR_DOT, -1, -1, 2, 2, 1.0f);

        // 左水平线: 6x2
        renderTextureWithColor(gui, CROSSHAIR_H, -1 - gap - 6, -1, 6, 2, 1.0f);
        // 右水平线: 6x2
        renderTextureWithColor(gui, CROSSHAIR_H, 1 + gap, -1, 6, 2, 1.0f);

        // 上垂直线: 2x6
        renderTextureWithColor(gui, CROSSHAIR_V, -1, -1 - gap - 6, 2, 6, 1.0f);
        // 下垂直线: 2x6
        renderTextureWithColor(gui, CROSSHAIR_V, -1, 1 + gap, 2, 6, 1.0f);

        gui.pose().popPose();
    }

    private static void renderTextureWithColor(GuiGraphics gui, ResourceLocation tex, float x, float y, float w,
            float h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        var tes = Tesselator.getInstance();
        var buf = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        int argb = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        buf.addVertex(matrix, x, y, 0).setUv(0f, 0f).setColor(argb);
        buf.addVertex(matrix, x, y + h, 0).setUv(0f, 1f).setColor(argb);
        buf.addVertex(matrix, x + w, y + h, 0).setUv(1f, 1f).setColor(argb);
        buf.addVertex(matrix, x + w, y, 0).setUv(1f, 0f).setColor(argb);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    public static void render(GuiGraphics gui) {
        if (!DJModeManagerClient.getInstance().isInDJMode())
            return;
        var optSession = DJModeManagerClient.getInstance().getSession();
        if (optSession.isEmpty())
            return;
        DJSessionClient session = optSession.get();

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int centerX = screenW / 2;
        int judgeY = (int) (screenH * JUDGE_Y_RATIO);
        long currentTimeMs = session.getCurrentTimeMs();

        // 获取曲目设置
        TrackSettings settings = session.getTrackPack().settings();
        boolean isBeatMode = "beat".equals(settings.crosshairMode());
        float FADE_IN_VAL = isBeatMode ? settings.crosshairBeatCount() : settings.crosshairTimeMs();

        List<BeatEvent> combatLine = session.getTrackPack().timeline().combatLine();

        // ─── 1. 节拍线 ───
        if (combatLine != null) {
            for (int j = 0; j < combatLine.size(); j++) {
                BeatEvent beat = combatLine.get(j);
                long beatT = (long) beat.t();
                long timeUntil = beatT - currentTimeMs;

                // 彻底消散了就不再渲染
                if (timeUntil < -FADE_OUT_MS)
                    continue;

                long duration = (long) FADE_IN_VAL;
                if (isBeatMode) {
                    long tSpawn = getTimeAtVisualIndex(combatLine, j - FADE_IN_VAL);
                    duration = beatT - tSpawn;
                    if (duration <= 0)
                        duration = 1;
                }

                float frac = (float) timeUntil / duration;
                if (frac > 1f)
                    continue;

                BeatDefinition def = session.getTrackPack().getDefinition(beat.type());
                int rgb = parseHexColor(def != null && def.color() != null ? def.color() : "#FFFFFF");

                // --- 被攻击解除的节拍：冻结位置 + 瞬移（如果 Hit) + 快速消散 ---
                long[] dismissed = dismissedBeats.get(beatT);
                if (dismissed != null) {
                    long elapsed = System.currentTimeMillis() - dismissed[0];
                    if (elapsed >= DISMISS_DURATION_MS) {
                        dismissedBeats.remove(beatT);
                        continue;
                    }
                    float alpha = 1f - (float) elapsed / DISMISS_DURATION_MS;

                    float frozenFrac;
                    // 如果判定成功 (isHit == 1)，瞬移到判定线位置；否则冻结在攻击位置
                    if (dismissed[2] == 1L) {
                        frozenFrac = 0f;
                    } else {
                        long sessionTimeMs = dismissed[1];
                        long frozenTimeUntil = beatT - sessionTimeMs;
                        frozenFrac = (float) frozenTimeUntil / duration;
                    }
                    frozenFrac = Math.max(0f, frozenFrac); // 无论如何不能超过判定线的位置

                    int frozenY = judgeY + (int) (frozenFrac * MAX_SPAWN_DIST_Y);
                    float frozenScale = calcScale(frozenFrac);
                    renderVShape(gui, centerX, frozenY, frozenScale, alpha * 0.9f, rgb);
                    continue;
                }

                // --- 正常流动节拍 ---
                // 到达判定线时定住（坐标和大小都用 0 来计算，也就是判定线的位置）
                float clampedFrac = Math.max(0f, frac);

                int beatY = judgeY + (int) (clampedFrac * MAX_SPAWN_DIST_Y);
                float scale = calcScale(clampedFrac);

                float alpha;
                if (timeUntil > 0) {
                    alpha = 1f - clampedFrac;
                } else {
                    // 过判定线后在原地快速消散，依赖时间
                    alpha = 1f + (float) timeUntil / FADE_OUT_MS;
                }
                alpha = Math.max(0f, Math.min(1f, alpha));

                renderVShape(gui, centerX, beatY, scale, alpha * 0.85f, rgb);
            }
        }

        // ─── 2. 固定判定线 ───
        renderJudgmentLine(gui, centerX, judgeY);
    }

    /**
     * 根据离判定线的距离比例 frac (0=到达, 1=最远) 计算缩放
     */
    private static float calcScale(float frac) {
        if (frac <= 0f)
            return JUDGE_SCALE;
        float t = 1f - Math.min(1f, frac);
        return MIN_SCALE + (JUDGE_SCALE - MIN_SCALE) * t;
    }

    /**
     * 根据索引计算在对应排布上它发生的物理时间（即 getVisualIndex 的反函数）
     */
    private static long getTimeAtVisualIndex(List<BeatEvent> line, float vIndex) {
        return otto.djgun.djcraft.util.BeatGridUtil.getTimeAtVirtualBeat((double) vIndex, line);
    }

    private static void renderJudgmentLine(GuiGraphics gui, int cx, int y) {
        int rgb = 0xDDEEFF;
        float alpha = 0.55f;
        if (feedbackStartMs >= 0) {
            long elapsed = System.currentTimeMillis() - feedbackStartMs;
            if (elapsed < FEEDBACK_DURATION_MS) {
                float t = 1f - (float) elapsed / FEEDBACK_DURATION_MS;
                rgb = feedbackIsHit ? 0x44FF99 : 0xFF4444;
                alpha = 0.6f + 0.4f * t;
            } else {
                feedbackStartMs = -1;
            }
        }
        renderVShape(gui, cx, y, JUDGE_SCALE, alpha, rgb);
    }

    private static void renderVShape(GuiGraphics gui, int centerX, int y, float scale, float alpha, int rgb) {
        int w = (int) (TEX_W * scale);
        int h = (int) (TEX_H * scale);
        int x = centerX - w / 2;

        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, VSHAPE_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(r, g, b, alpha);

        var tes = Tesselator.getInstance();
        var buf = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        int argb = ((int) (alpha * 255) << 24) | (((int) (r * 255)) << 16) | (((int) (g * 255)) << 8) | (int) (b * 255);

        buf.addVertex(matrix, x, y, 0).setUv(0f, 0f).setColor(argb);
        buf.addVertex(matrix, x, y + h, 0).setUv(0f, 1f).setColor(argb);
        buf.addVertex(matrix, x + w, y + h, 0).setUv(1f, 1f).setColor(argb);
        buf.addVertex(matrix, x + w, y, 0).setUv(1f, 0f).setColor(argb);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static int parseHexColor(String hex) {
        try {
            String s = hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseUnsignedInt(s, 16) & 0x00FFFFFF;
        } catch (Exception e) {
            return 0xFFFFFF;
        }
    }
}

package otto.djgun.djcraft.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.config.DJClientConfig;
import otto.djgun.djcraft.client.texture.DJBeatTextureLibrary;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.combat.client.DJDeferredDamageIndicatorState;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.BeatPostJudgmentBehavior;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

/** Full-color falling chart presentation driven exclusively by the DJ session audio clock. */
@OnlyIn(Dist.CLIENT)
public final class DJFallingBeatRenderer {
    private static final int MAX_SPAWN_ADVANCE_MS = 60_000;
    private static final long MAX_PAST_VISUAL_WINDOW_MS = 240_000L;
    private static final long MISS_FLASH_MS = 160L;
    private static final float BASE_MARKER_SIZE = 32.0f;
    private static final float IMPACT_QUAD_HALF_WIDTH = 82.0f;
    private static final float IMPACT_QUAD_HALF_HEIGHT = 44.0f;

    private static final Map<Integer, JudgmentVisual> JUDGED_BEATS = new HashMap<>();
    private static final Deque<ImpactWave> IMPACT_WAVES = new ArrayDeque<>();
    private static ShaderInstance impactRingShader;
    private static long activeSessionId = Long.MIN_VALUE;
    private static long lastSessionTimeMs = Long.MIN_VALUE;
    private static long missFlashStartedMs = Long.MIN_VALUE;

    private DJFallingBeatRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "falling_impact_ring"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            event.registerShader(shader, loaded -> impactRingShader = loaded);
        } catch (IOException exception) {
            impactRingShader = null;
            DJCraft.LOGGER.error("Failed to load falling beat impact ring shader", exception);
        }
    }

    public static void reset() {
        JUDGED_BEATS.clear();
        IMPACT_WAVES.clear();
        activeSessionId = Long.MIN_VALUE;
        lastSessionTimeMs = Long.MIN_VALUE;
        missFlashStartedMs = Long.MIN_VALUE;
    }

    public static void notifyJudgment(DJSessionClient session, HitResult result) {
        notifyJudgment(session, result, false);
    }

    public static void notifyJudgment(
            DJSessionClient session, HitResult result, boolean categoryMatched) {
        if (session == null || result == null) {
            return;
        }
        ensureSession(session.getSessionId(), result.judgedAtMs());
        BeatDefinition definition = result.beatData();
        BeatEvent event = result.beatEvent();
        if (result.isHit() && definition != null && event != null) {
            IMPACT_WAVES.addLast(new ImpactWave(
                    result.judgedAtMs(), definition.landingXPercent(), parseHexColor(definition.color())));
        } else if (!result.isHit()) {
            missFlashStartedMs = result.judgedAtMs();
        }

        if (definition == null || event == null || result.beatIndex() < 0) {
            return;
        }
        BeatPostJudgmentBehavior behavior =
                definition.behaviorAfterJudgment(result.isHit(), categoryMatched);
        if (behavior != BeatPostJudgmentBehavior.NONE) {
            JUDGED_BEATS.put(result.beatIndex(), new JudgmentVisual(
                    result.beatIndex(), event.t(), result.judgedAtMs(), definition, behavior));
        }
    }

    public static void render(GuiGraphics gui) {
        var activeSession = DJModeManagerClient.getInstance().getActiveSession();
        if (activeSession.isEmpty()) {
            reset();
            return;
        }
        DJSessionClient session = activeSession.get();
        long currentTimeMs = session.getCurrentTimeMs();
        ensureSession(session.getSessionId(), currentTimeMs);
        if (lastSessionTimeMs != Long.MIN_VALUE && currentTimeMs + 50L < lastSessionTimeMs) {
            reset();
            ensureSession(session.getSessionId(), currentTimeMs);
        }
        lastSessionTimeMs = currentTimeMs;

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        float judgmentY = screenHeight * (DJClientConfig.fallingJudgeLineYPercent() / 100.0f);
        List<BeatEvent> line = session.getTrackPack().timeline().combatLine();

        renderJudgmentLine(gui, session, line, currentTimeMs, screenWidth, judgmentY);
        renderFallingBeats(gui, session, line, currentTimeMs, screenWidth, screenHeight, judgmentY);
        renderJudgmentVisuals(gui, session, currentTimeMs, screenWidth, judgmentY);
        renderImpactWaves(gui, currentTimeMs, screenWidth, judgmentY);
    }

    private static void renderJudgmentLine(GuiGraphics gui, DJSessionClient session, List<BeatEvent> line,
            long currentTimeMs, int screenWidth, float judgmentY) {
        int y = Math.round(judgmentY);
        boolean deferredDamagePending = DJDeferredDamageIndicatorState.hasPending(session.getSessionId());
        int baselineColor = deferredDamagePending ? 0xAAFFD83D : 0x44DDEEFF;
        for (int x = 2; x < screenWidth - 2; x += 11) {
            gui.fill(x, y, Math.min(x + 6, screenWidth - 2), y + 1, baselineColor);
        }

        float progress = baselineFillProgress(session, line, currentTimeMs);
        if (progress > 0.0f) {
            int center = screenWidth / 2;
            int half = Math.round(center * progress);
            int glowColor = deferredDamagePending ? 0x33FFD83D : 0x2244CCFF;
            int fillColor = deferredDamagePending ? 0xCCFFD83D : 0x9955DDFF;
            gui.fill(center - half, y - 2, center + half, y + 3, glowColor);
            gui.fill(center - half, y - 1, center + half, y + 2, fillColor);
        }

        long missAge = currentTimeMs - missFlashStartedMs;
        if (!deferredDamagePending && missFlashStartedMs != Long.MIN_VALUE
                && missAge >= 0L && missAge < MISS_FLASH_MS) {
            float alpha = 1.0f - (float) missAge / MISS_FLASH_MS;
            int argb = (Math.round(190.0f * alpha) << 24) | 0x00FF3344;
            gui.fill(0, y - 2, screenWidth, y + 3, argb);
        }
    }

    private static float baselineFillProgress(DJSessionClient session, List<BeatEvent> line, long currentTimeMs) {
        if (line.isEmpty()) {
            return 0.0f;
        }
        int insertion = lowerBound(line, currentTimeMs);
        int nextIndex = -1;
        for (int index = insertion; index < line.size(); index++) {
            if (session.getTrackPack().resolveDefinition(line.get(index)).canAttack()) {
                nextIndex = index;
                break;
            }
        }
        if (nextIndex < 0) {
            return 0.0f;
        }

        BeatEvent next = line.get(nextIndex);
        long cycleStart = next.t()
                - session.getTrackPack().resolveDefinition(next).spawnAdvanceMs();
        for (int index = nextIndex - 1; index >= 0; index--) {
            BeatEvent candidate = line.get(index);
            if (candidate.t() < next.t()
                    && session.getTrackPack().resolveDefinition(candidate).canAttack()) {
                cycleStart = candidate.t();
                break;
            }
        }
        return FallingBeatMath.fillProgress(currentTimeMs, cycleStart, next.t());
    }

    private static void renderFallingBeats(GuiGraphics gui, DJSessionClient session, List<BeatEvent> line,
            long currentTimeMs, int screenWidth, int screenHeight, float judgmentY) {
        if (line.isEmpty()) {
            return;
        }
        int startIndex = lowerBound(line, currentTimeMs - MAX_PAST_VISUAL_WINDOW_MS);
        long latestBeatTime = currentTimeMs + MAX_SPAWN_ADVANCE_MS;
        for (int index = startIndex; index < line.size(); index++) {
            BeatEvent event = line.get(index);
            if (event.t() > latestBeatTime) {
                break;
            }
            JudgmentVisual judged = JUDGED_BEATS.get(index);
            if (judged != null && judged.behavior() != BeatPostJudgmentBehavior.NONE) {
                continue;
            }
            BeatDefinition definition = session.getTrackPack().resolveDefinition(event);
            long spawnTime = (long) event.t() - definition.spawnAdvanceMs();
            if (currentTimeMs < spawnTime) {
                continue;
            }
            DJBeatTextureLibrary.AnimatedTexture texture = DJBeatTextureLibrary.getInstance()
                    .resolve(session.getTrackPack().id(), definition.texture());
            MarkerSize size = markerSize(texture, definition.scale());
            float topY = FallingBeatMath.markerTopY(
                    currentTimeMs, event.t(), definition.spawnAdvanceMs(), judgmentY, size.height());
            if (topY >= screenHeight || topY + size.height() <= 0.0f) {
                continue;
            }
            long elapsedSinceSpawn = Math.max(0L, currentTimeMs - spawnTime);
            float centerX = screenWidth * definition.landingXPercent() / 100.0f;
            renderMarker(gui, texture.frameAt(elapsedSinceSpawn), centerX, topY,
                    size.width(), size.height(),
                    FallingBeatMath.rotationDegrees(elapsedSinceSpawn, definition.rotationRpm()),
                    1.0f);
        }
    }

    private static void renderJudgmentVisuals(GuiGraphics gui, DJSessionClient session,
            long currentTimeMs, int screenWidth, float judgmentY) {
        for (JudgmentVisual visual : JUDGED_BEATS.values()) {
            long age = currentTimeMs - visual.judgedAtMs();
            if (age < 0L) {
                continue;
            }
            FallingBeatMath.EffectFrame effect = FallingBeatMath.effectFrame(visual.behavior(), age);
            if (!effect.visible()) {
                continue;
            }
            BeatDefinition definition = visual.definition();
            DJBeatTextureLibrary.AnimatedTexture texture = DJBeatTextureLibrary.getInstance()
                    .resolve(session.getTrackPack().id(), definition.texture());
            MarkerSize baseSize = markerSize(texture, definition.scale());
            long spawnTime = visual.beatTimeMs() - definition.spawnAdvanceMs();
            float baseTop = FallingBeatMath.markerTopY(
                    visual.judgedAtMs(), visual.beatTimeMs(),
                    definition.spawnAdvanceMs(), judgmentY, baseSize.height());
            long elapsedSinceSpawn = Math.max(0L, currentTimeMs - spawnTime);
            float centerX = screenWidth * definition.landingXPercent() / 100.0f;
            float width = baseSize.width() * effect.scale();
            float height = baseSize.height() * effect.scale();
            float topY = baseTop + effect.offsetY() - (height - baseSize.height()) * 0.5f;
            renderMarker(gui, texture.frameAt(elapsedSinceSpawn), centerX, topY, width, height,
                    FallingBeatMath.rotationDegrees(elapsedSinceSpawn, definition.rotationRpm()),
                    effect.alpha());
        }
    }

    private static void renderImpactWaves(GuiGraphics gui, long currentTimeMs,
            int screenWidth, float judgmentY) {
        IMPACT_WAVES.removeIf(wave ->
                currentTimeMs - wave.startedMs() >= FallingBeatMath.IMPACT_WAVE_MS);
        ShaderInstance shader = impactRingShader;
        if (shader == null) {
            return;
        }
        for (ImpactWave wave : IMPACT_WAVES) {
            long age = currentTimeMs - wave.startedMs();
            FallingBeatMath.ImpactWaveFrame frame = FallingBeatMath.impactWaveFrame(age);
            if (!frame.visible()) {
                continue;
            }
            float centerX = screenWidth * wave.landingXPercent() / 100.0f;
            float progress = Math.max(0.0f, Math.min(1.0f,
                    (float) age / FallingBeatMath.IMPACT_WAVE_MS));
            renderImpactShaderQuad(gui, shader, centerX, judgmentY, wave.rgb(), progress);
        }
    }

    private static void renderImpactShaderQuad(GuiGraphics gui, ShaderInstance shader,
            float centerX, float centerY, int rgb, float progress) {
        float red = ((rgb >>> 16) & 0xFF) / 255.0f;
        float green = ((rgb >>> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;
        shader.safeGetUniform("RingColor").set(red, green, blue, 1.0f);
        shader.safeGetUniform("Progress").set(progress);

        gui.pose().pushPose();
        gui.pose().translate(0.0f, 0.0f, 200.0f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        float left = centerX - IMPACT_QUAD_HALF_WIDTH;
        float right = centerX + IMPACT_QUAD_HALF_WIDTH;
        float top = centerY - IMPACT_QUAD_HALF_HEIGHT;
        float bottom = centerY + IMPACT_QUAD_HALF_HEIGHT;
        buffer.addVertex(matrix, left, top, 0.0f).setUv(0.0f, 0.0f).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, left, bottom, 0.0f).setUv(0.0f, 1.0f).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, right, bottom, 0.0f).setUv(1.0f, 1.0f).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, right, top, 0.0f).setUv(1.0f, 0.0f).setColor(0xFFFFFFFF);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        gui.pose().popPose();
    }

    private static void renderMarker(GuiGraphics gui, ResourceLocation texture, float centerX, float topY,
            float width, float height, float rotationDegrees, float alpha) {
        if (alpha <= 0.0f || width <= 0.0f || height <= 0.0f) {
            return;
        }
        gui.pose().pushPose();
        gui.pose().translate(centerX, topY + height * 0.5f, 0.0f);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        int argb = (Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, alpha))) << 24) | 0x00FFFFFF;
        float left = -width * 0.5f;
        float right = width * 0.5f;
        float top = -height * 0.5f;
        float bottom = height * 0.5f;
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        buffer.addVertex(matrix, left, top, 0.0f).setUv(0.0f, 0.0f).setColor(argb);
        buffer.addVertex(matrix, left, bottom, 0.0f).setUv(0.0f, 1.0f).setColor(argb);
        buffer.addVertex(matrix, right, bottom, 0.0f).setUv(1.0f, 1.0f).setColor(argb);
        buffer.addVertex(matrix, right, top, 0.0f).setUv(1.0f, 0.0f).setColor(argb);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        gui.pose().popPose();
    }

    private static MarkerSize markerSize(DJBeatTextureLibrary.AnimatedTexture texture, float configuredScale) {
        float scale = Float.isFinite(configuredScale) && configuredScale > 0.0f
                ? Math.max(0.05f, Math.min(8.0f, configuredScale))
                : 1.0f;
        int longest = Math.max(texture.width(), texture.height());
        float normalization = BASE_MARKER_SIZE * scale / Math.max(1, longest);
        return new MarkerSize(texture.width() * normalization, texture.height() * normalization);
    }

    private static int lowerBound(List<BeatEvent> line, long timeMs) {
        int low = 0;
        int high = line.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (line.get(middle).t() < timeMs) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void ensureSession(long sessionId, long currentTimeMs) {
        if (sessionId != activeSessionId) {
            reset();
            activeSessionId = sessionId;
            lastSessionTimeMs = currentTimeMs;
        }
    }

    private static int parseHexColor(String hex) {
        try {
            String value = hex != null && hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseUnsignedInt(value, 16) & 0x00FFFFFF;
        } catch (RuntimeException ignored) {
            return 0x55DDFF;
        }
    }

    private record MarkerSize(float width, float height) {
    }

    private record JudgmentVisual(int beatIndex, long beatTimeMs, long judgedAtMs,
            BeatDefinition definition, BeatPostJudgmentBehavior behavior) {
    }

    private record ImpactWave(long startedMs, float landingXPercent, int rgb) {
    }
}

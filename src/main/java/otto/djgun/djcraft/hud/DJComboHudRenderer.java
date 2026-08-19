package otto.djgun.djcraft.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Renders the resource-driven combo counter in the upper-right HUD. */
@OnlyIn(Dist.CLIENT)
public final class DJComboHudRenderer {
    private static final int DIGIT_SIZE = 16;
    private static final int DIGIT_ADVANCE = 12;
    private static final float BASE_SCALE = 2.0f;
    private static final long TRAIL_EMIT_INTERVAL_MS = 36L;
    private static final int MAX_TRAILS = 6;
    private static final int DISAPPEAR_BRANCHES_PER_DIGIT = 6;
    private static final Deque<TrailEcho> TRAILS = new ArrayDeque<>();
    private static final List<DisappearEcho> DISAPPEAR_ECHOES = new ArrayList<>();

    private static long activeSessionId = Long.MIN_VALUE;
    private static String activePackId;
    private static int lastCombo;
    private static long bounceStartedMs = -DJComboHudAnimation.BOUNCE_DURATION_MS;
    private static long lastTrailEmitMs = Long.MIN_VALUE;
    private static ShaderInstance glowShader;

    private DJComboHudRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "combo_glow"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            event.registerShader(shader, loaded -> glowShader = loaded);
        } catch (IOException exception) {
            glowShader = null;
            DJCraft.LOGGER.error("Failed to load combo HUD glow shader", exception);
        }
    }

    public static void render(GuiGraphics gui) {
        long nowMs = System.currentTimeMillis();
        var sessionOpt = DJModeManagerClient.getInstance().getActiveSession();
        if (sessionOpt.isEmpty()) {
            if (lastCombo > 0) {
                spawnDisappearance(Integer.toString(lastCombo), activePackId, lastCombo, nowMs);
            }
            activeSessionId = Long.MIN_VALUE;
            activePackId = null;
            lastCombo = 0;
            bounceStartedMs = -DJComboHudAnimation.BOUNCE_DURATION_MS;
            clearTrails();
            renderDisappearance(gui, nowMs);
            return;
        }

        DJSessionClient session = sessionOpt.get();
        int combo = session.getCombo();
        String packId = session.getTrackPack().id();
        if (session.getSessionId() != activeSessionId) {
            activeSessionId = session.getSessionId();
            activePackId = packId;
            lastCombo = 0;
            clearTrails();
        }
        if (combo <= 0) {
            if (lastCombo > 0) {
                spawnDisappearance(Integer.toString(lastCombo), packId, lastCombo, nowMs);
            }
            lastCombo = 0;
            clearTrails();
            renderDisappearance(gui, nowMs);
            return;
        }

        if (combo > lastCombo) {
            bounceStartedMs = nowMs;
        }
        lastCombo = combo;

        String digits = Integer.toString(combo);
        updateTrails(digits, packId, combo, nowMs);
        float contentWidth = DIGIT_SIZE + (digits.length() - 1) * DIGIT_ADVANCE;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        float anchorX = screenWidth * 0.80f;
        float anchorY = 82.0f;
        DJComboHudAnimation.Frame frame = DJComboHudAnimation.sample(nowMs - bounceStartedMs);

        renderDisappearance(gui, nowMs);

        gui.pose().pushPose();
        gui.pose().translate(anchorX, anchorY + frame.bounceY(), 0.0f);
        float scale = BASE_SCALE * frame.scale() * DJComboHudAnimation.comboScale(combo);
        gui.pose().scale(scale, scale, 1.0f);

        if (DJComboHudAnimation.hasTrail(combo)) {
            renderTrails(gui, nowMs);
        }
        if (DJComboHudAnimation.hasGlow(combo)) {
            renderGlow(gui, digits, packId, combo, contentWidth, nowMs, DJComboHudAnimation.hasTrail(combo));
        }
        renderDigits(gui, digits, packId, combo, -contentWidth / 2.0f, 0.0f, 1.0f,
                nowMs, DJComboHudAnimation.hasTrail(combo));

        gui.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
    }

    private static void updateTrails(String digits, String packId, int combo, long nowMs) {
        if (!DJComboHudAnimation.hasTrail(combo)) {
            clearTrails();
            return;
        }
        if (lastTrailEmitMs == Long.MIN_VALUE) {
            lastTrailEmitMs = nowMs - TRAIL_EMIT_INTERVAL_MS;
        }
        while (nowMs - lastTrailEmitMs >= TRAIL_EMIT_INTERVAL_MS) {
            lastTrailEmitMs += TRAIL_EMIT_INTERVAL_MS;
            TRAILS.addLast(new TrailEcho(digits, packId, combo, lastTrailEmitMs));
            while (TRAILS.size() > MAX_TRAILS) {
                TRAILS.removeFirst();
            }
        }
        TRAILS.removeIf(echo -> nowMs - echo.spawnedMs() >= DJComboHudAnimation.TRAIL_LIFETIME_MS);
    }

    private static void renderTrails(GuiGraphics gui, long nowMs) {
        ShaderInstance shader = glowShader;
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("EffectMode").set(1.0f);
        for (TrailEcho echo : TRAILS) {
            long ageMs = nowMs - echo.spawnedMs();
            float width = DIGIT_SIZE + (echo.digits().length() - 1) * DIGIT_ADVANCE;
            float x = -width / 2.0f + DJComboHudAnimation.trailOffsetX(ageMs);
            float y = ageMs * -0.006f;
            renderMaskedDigits(gui, echo.digits(), echo.packId(), echo.combo(), x, y,
                    DJComboHudAnimation.trailAlpha(ageMs), nowMs, ageMs, shader);
        }
    }

    private static void renderGlow(GuiGraphics gui, String digits, String packId, int combo,
            float contentWidth, long nowMs, boolean wave) {
        ShaderInstance shader = glowShader;
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("EffectMode").set(0.0f);
        shader.safeGetUniform("GlowStrength").set(1.0f);
        float startX = -contentWidth / 2.0f;
        DJComboHudAnimation.Rgb rainbow = DJComboHudAnimation.hasRainbowTrail(combo)
                ? DJComboHudAnimation.rainbowTrailColor(nowMs, 0L)
                : null;
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            float y = wave ? DJComboHudAnimation.waveOffsetY(nowMs, index, combo) : 0.0f;
            DJComboTextureLibrary.Style style = comboStyle(packId, combo, digit);
            if (rainbow == null) {
                setEffectColor(shader, style, 0.55f);
            } else {
                setEffectColor(shader, rainbow, 0.55f);
            }
            renderGlowTexture(gui, style.texture(), startX + index * DIGIT_ADVANCE, y, shader);
        }
    }

    private static void spawnDisappearance(String digits, String packId, int combo, long nowMs) {
        float width = DIGIT_SIZE + (digits.length() - 1) * DIGIT_ADVANCE;
        float startX = -width / 2.0f;
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            float digitX = startX + index * DIGIT_ADVANCE;
            DJComboTextureLibrary.Style style = comboStyle(packId, combo, digit);
            for (int branch = 0; branch < DISAPPEAR_BRANCHES_PER_DIGIT; branch++) {
                DISAPPEAR_ECHOES.add(new DisappearEcho(
                        style, digitX, DJComboHudAnimation.comboScale(combo), branch, nowMs));
            }
        }
    }

    private static void renderDisappearance(GuiGraphics gui, long nowMs) {
        DISAPPEAR_ECHOES.removeIf(echo -> nowMs - echo.spawnedMs() >= DJComboHudAnimation.DISAPPEAR_LIFETIME_MS);
        ShaderInstance shader = glowShader;
        if (DISAPPEAR_ECHOES.isEmpty() || shader == null) {
            return;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        shader.safeGetUniform("EffectMode").set(1.0f);
        gui.pose().pushPose();
        gui.pose().translate(screenWidth * 0.80f, 82.0f, 0.0f);

        for (DisappearEcho echo : DISAPPEAR_ECHOES) {
            long ageMs = nowMs - echo.spawnedMs();
            float x = echo.baseX() + DJComboHudAnimation.disappearOffsetX(ageMs, echo.branch());
            float y = DJComboHudAnimation.disappearOffsetY(ageMs, echo.branch());
            float scale = DJComboHudAnimation.disappearScale(ageMs);
            gui.pose().pushPose();
            gui.pose().scale(BASE_SCALE * echo.comboScale(), BASE_SCALE * echo.comboScale(), 1.0f);
            gui.pose().translate(x + DIGIT_SIZE / 2.0f, y + DIGIT_SIZE / 2.0f, 0.0f);
            gui.pose().scale(scale, scale, 1.0f);
            setEffectColor(shader, echo.style(), 1.0f);
            renderQuad(gui, echo.style().texture(), -DIGIT_SIZE / 2.0f, -DIGIT_SIZE / 2.0f,
                    DIGIT_SIZE, 0.0f, 1.0f, DJComboHudAnimation.disappearAlpha(ageMs), shader);
            gui.pose().popPose();
        }
        gui.pose().popPose();
    }

    private static void renderDigits(GuiGraphics gui, String digits, String packId, int combo,
            float startX, float y, float alpha, long nowMs, boolean wave) {
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            float digitY = y + (wave ? DJComboHudAnimation.waveOffsetY(nowMs, index, combo) : 0.0f);
            renderTexture(gui, comboStyle(packId, combo, digit).texture(),
                    startX + index * DIGIT_ADVANCE, digitY, alpha);
        }
    }

    private static void renderMaskedDigits(GuiGraphics gui, String digits, String packId, int combo,
            float startX, float y, float alpha, long nowMs, long ageMs, ShaderInstance shader) {
        DJComboHudAnimation.Rgb rainbow = DJComboHudAnimation.hasRainbowTrail(combo)
                ? DJComboHudAnimation.rainbowTrailColor(nowMs, ageMs)
                : null;
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            float digitY = y + DJComboHudAnimation.waveOffsetY(nowMs, index, combo);
            DJComboTextureLibrary.Style style = comboStyle(packId, combo, digit);
            if (rainbow == null) {
                setEffectColor(shader, style, 1.0f);
            } else {
                setEffectColor(shader, rainbow, 1.0f);
            }
            renderQuad(gui, style.texture(), startX + index * DIGIT_ADVANCE, digitY,
                    DIGIT_SIZE, 0.0f, 1.0f, alpha, shader);
        }
    }

    private static DJComboTextureLibrary.Style comboStyle(String packId, int combo, int digit) {
        return DJComboTextureLibrary.getInstance().resolve(packId, combo, digit);
    }

    private static void setEffectColor(ShaderInstance shader, DJComboTextureLibrary.Style style, float alpha) {
        shader.safeGetUniform("GlowColor").set(style.red(), style.green(), style.blue(), alpha);
    }

    private static void setEffectColor(ShaderInstance shader, DJComboHudAnimation.Rgb color, float alpha) {
        shader.safeGetUniform("GlowColor").set(color.red(), color.green(), color.blue(), alpha);
    }

    private static void renderGlowTexture(GuiGraphics gui, ResourceLocation texture, float x, float y,
            ShaderInstance shader) {
        float border = 5.0f;
        renderQuad(gui, texture, x - border, y - border, DIGIT_SIZE + border * 2.0f,
                -border / DIGIT_SIZE, 1.0f + border / DIGIT_SIZE, 0.95f, shader);
    }

    private static void renderTexture(GuiGraphics gui, ResourceLocation texture, float x, float y, float alpha) {
        renderQuad(gui, texture, x, y, DIGIT_SIZE, 0.0f, 1.0f, alpha, null);
    }

    private static void renderQuad(GuiGraphics gui, ResourceLocation texture, float x, float y, float size,
            float uvMin, float uvMax, float alpha, ShaderInstance shader) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        if (shader == null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        } else {
            RenderSystem.setShader(() -> shader);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        int channel = Math.clamp(Math.round(alpha * 255.0f), 0, 255);
        int argb = (channel << 24) | 0xFFFFFF;
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        buffer.addVertex(matrix, x, y, 0.0f).setUv(uvMin, uvMin).setColor(argb);
        buffer.addVertex(matrix, x, y + size, 0.0f).setUv(uvMin, uvMax).setColor(argb);
        buffer.addVertex(matrix, x + size, y + size, 0.0f).setUv(uvMax, uvMax).setColor(argb);
        buffer.addVertex(matrix, x + size, y, 0.0f).setUv(uvMax, uvMin).setColor(argb);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void clearTrails() {
        TRAILS.clear();
        lastTrailEmitMs = Long.MIN_VALUE;
    }

    private record TrailEcho(String digits, String packId, int combo, long spawnedMs) {
    }

    private record DisappearEcho(
            DJComboTextureLibrary.Style style, float baseX, float comboScale, int branch, long spawnedMs) {
    }
}

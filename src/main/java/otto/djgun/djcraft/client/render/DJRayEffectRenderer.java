package otto.djgun.djcraft.client.render;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;

/** Short-lived predicted and authoritative world-space ray effects. */
public final class DJRayEffectRenderer {
    private static final int MAX_EFFECTS = 128;
    private static final long PREDICTION_RECONCILE_MS = 2_000L;
    private static final int SPHERE_LATITUDES = 16;
    private static final int SPHERE_LONGITUDES = 24;
    private static final Vec3[][] UNIT_SPHERE = createUnitSphere();
    private static final Deque<Effect> EFFECTS = new ArrayDeque<>();
    private static ShaderInstance shader;

    private DJRayEffectRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance instance = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "ray_effect"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            event.registerShader(instance, loaded -> shader = loaded);
        } catch (IOException exception) {
            shader = null;
            DJCraft.LOGGER.error("Failed to load ray effect shader", exception);
        }
    }

    public static void predict(long sequence, UUID shooterId, InteractionHand hand,
            ResourceLocation effect, Vec3 origin, Vec3 end) {
        predict(sequence, shooterId, hand, effect, origin, end, 0.0);
    }

    public static void predict(long sequence, UUID shooterId, InteractionHand hand,
            ResourceLocation effect, Vec3 origin, Vec3 end, double shockwaveRadius) {
        if (EFFECTS.stream().anyMatch(queued -> sameAction(queued, sequence, shooterId)
                && !queued.predicted())) {
            return;
        }
        EFFECTS.removeIf(queued -> sameAction(queued, sequence, shooterId));
        add(new Effect(sequence, shooterId, hand, effect, origin, end, List.of(),
                shockwaveRadius, nowMs(), true));
    }

    public static void acceptAuthoritative(DJRayEffectPayload payload) {
        Effect existing = EFFECTS.stream()
                .filter(effect -> sameAction(effect, payload.sequence(), payload.shooterId()))
                .findFirst().orElse(null);
        EFFECTS.removeIf(effect -> sameAction(effect, payload.sequence(), payload.shooterId()));
        add(new Effect(payload.sequence(), payload.shooterId(), payload.hand(), payload.effect(),
                payload.origin(), payload.end(), payload.contacts(),
                payload.shockwaveRadius(), existing == null ? nowMs() : existing.createdAtMs(), false));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()
                || shader == null) {
            return;
        }
        long now = nowMs();
        EFFECTS.removeIf(effect -> expired(effect, now));
        if (EFFECTS.isEmpty()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            Vec3 camera = event.getCamera().getPosition();
            for (Effect effect : EFFECTS) {
                DJRayEffectProfile profile = DJRayEffectLibrary.getInstance().resolve(effect.effect()).orElse(null);
                if (profile != null && now - effect.createdAtMs()
                        < lifetime(profile, effect)) {
                    renderEffect(event.getPoseStack(), camera, effect, profile, now);
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    public static void clear() {
        EFFECTS.clear();
    }

    static int queuedEffectCount() {
        return EFFECTS.size();
    }

    private static boolean sameAction(Effect effect, long sequence, UUID shooterId) {
        return effect.sequence() == sequence && effect.shooterId().equals(shooterId);
    }

    private static void renderEffect(PoseStack poseStack, Vec3 camera, Effect effect,
            DJRayEffectProfile profile, long now) {
        shader.safeGetUniform("Time").set((float) (now % 100_000L) / 1_000.0F * profile.pulseSpeed());
        float beamProgress = Math.clamp((float) (now - effect.createdAtMs()) / profile.beamLifetimeMs(), 0.0F, 1.0F);
        boolean widthAnimated = profile.beamWidthPeakScale() > 0.0F;
        float widthScale = beamWidthScale(
                profile.beamWidthStartScale(), profile.beamWidthPeakScale(), beamProgress);
        Vec3 start = muzzle(effect, profile);
        renderBeam(poseStack, camera, start, effect.end(), profile.haloWidth() * widthScale,
                profile.haloColor(), beamProgress, profile.beamFadeFromNear(), widthAnimated);
        renderBeam(poseStack, camera, start, effect.end(), profile.coreWidth() * widthScale,
                profile.coreColor(), beamProgress, profile.beamFadeFromNear(), widthAnimated);

        float burstProgress = Math.clamp((float) (now - effect.createdAtMs()) / profile.burstLifetimeMs(), 0.0F, 1.0F);
        float radius = burstRadius(profile.burstStartRadius(), profile.burstEndRadius(), burstProgress);
        renderBurst(poseStack, camera, start, radius * profile.muzzleBurstScale(),
                profile.coreColor(), burstProgress);
        for (Vec3 contact : effect.contacts()) {
            renderBurst(poseStack, camera, contact, radius * profile.contactBurstScale(),
                    profile.haloColor(), burstProgress);
        }
        renderBurst(poseStack, camera, effect.end(), radius * profile.endBurstScale(),
                profile.coreColor(), burstProgress);
        if (effect.shockwaveRadius() > 0.0 && profile.shockwaveLifetimeMs() > 0L) {
            float progress = Math.clamp((float) (now - effect.createdAtMs())
                    / profile.shockwaveLifetimeMs(), 0.0F, 1.0F);
            float shockwaveRadius = shockwaveRadius(profile.shockwaveStartRadius(),
                    (float) effect.shockwaveRadius(), progress);
            renderSphere(poseStack, camera, effect.end(), shockwaveRadius * 1.035F,
                    profile.shockwaveHaloColor(), progress);
            renderSphere(poseStack, camera, effect.end(), shockwaveRadius,
                    profile.shockwaveCoreColor(), progress);
        }
    }

    static float burstRadius(float startRadius, float peakRadius, float progress) {
        float clampedProgress = Math.clamp(progress, 0.0F, 1.0F);
        float expansion = (float) Math.sin(Math.PI * clampedProgress);
        return startRadius + (peakRadius - startRadius) * expansion;
    }

    static float shockwaveRadius(float startRadius, float endRadius, float progress) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - clamped, 3.0);
        return startRadius + (endRadius - startRadius) * eased;
    }

    static float beamWidthScale(float startScale, float peakScale, float progress) {
        if (peakScale <= 0.0F) {
            return 1.0F;
        }
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        float curve = (float) Math.sin(Math.PI * clamped);
        return clamped <= 0.5F
                ? startScale + (peakScale - startScale) * curve
                : peakScale * curve;
    }

    private static Vec3 muzzle(Effect effect, DJRayEffectProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.player != null
                && minecraft.player.getUUID().equals(effect.shooterId())
                && minecraft.options.getCameraType().isFirstPerson();
        Vec3 offset;
        if (firstPerson) {
            offset = effect.hand() == InteractionHand.MAIN_HAND
                    ? profile.firstPersonMainMuzzle() : profile.firstPersonOffhandMuzzle();
        } else {
            Vec3 thirdPerson = profile.thirdPersonMuzzle();
            double handedX = effect.hand() == InteractionHand.MAIN_HAND ? thirdPerson.x : -thirdPerson.x;
            offset = new Vec3(handedX, thirdPerson.y, thirdPerson.z);
        }
        return applyCameraOffset(effect.origin(), effect.end(), offset);
    }

    static Vec3 applyCameraOffset(Vec3 origin, Vec3 end, Vec3 offset) {
        Vec3 forward = end.subtract(origin).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-8) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();
        return origin.add(right.scale(offset.x)).add(up.scale(offset.y)).add(forward.scale(offset.z));
    }

    private static void renderBeam(PoseStack poseStack, Vec3 camera, Vec3 start, Vec3 end,
            float width, int color, float progress, boolean fadeFromNear, boolean widthAnimated) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 toCamera = camera.subtract(start.add(end).scale(0.5));
        Vec3 side = direction.normalize().cross(toCamera.normalize());
        if (side.lengthSqr() < 1.0E-8) {
            side = direction.normalize().cross(new Vec3(0.0, 1.0, 0.0));
        }
        if (side.lengthSqr() < 1.0E-8) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize().scale(width * 0.5);
        setShaderMode(0.0F, progress, fadeFromNear, widthAnimated);
        drawQuad(poseStack, camera, start.add(side), start.subtract(side), end.subtract(side), end.add(side), color);
    }

    private static void renderBurst(PoseStack poseStack, Vec3 camera, Vec3 center,
            float radius, int color, float progress) {
        Vec3 facing = camera.subtract(center);
        if (facing.lengthSqr() < 1.0E-8) {
            facing = new Vec3(0.0, 0.0, 1.0);
        }
        facing = facing.normalize();
        Vec3 right = new Vec3(0.0, 1.0, 0.0).cross(facing);
        if (right.lengthSqr() < 1.0E-8) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = facing.cross(right).normalize();
        setShaderMode(1.0F, progress, false, false);
        drawQuad(poseStack, camera,
                center.subtract(right.scale(radius)).add(up.scale(radius)),
                center.subtract(right.scale(radius)).subtract(up.scale(radius)),
                center.add(right.scale(radius)).subtract(up.scale(radius)),
                center.add(right.scale(radius)).add(up.scale(radius)), color);
    }

    private static void renderSphere(PoseStack poseStack, Vec3 camera, Vec3 center,
            float radius, int color, float progress) {
        if (radius <= 0.0F) {
            return;
        }
        setShaderMode(2.0F, progress, false, false);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = poseStack.last().pose();
        for (int latitude = 0; latitude < SPHERE_LATITUDES; latitude++) {
            float v0 = (float) latitude / SPHERE_LATITUDES;
            float v1 = (float) (latitude + 1) / SPHERE_LATITUDES;
            for (int longitude = 0; longitude < SPHERE_LONGITUDES; longitude++) {
                int nextLongitude = (longitude + 1) % SPHERE_LONGITUDES;
                float u0 = (float) longitude / SPHERE_LONGITUDES;
                float u1 = (float) (longitude + 1) / SPHERE_LONGITUDES;
                Vec3 a = center.add(UNIT_SPHERE[latitude][longitude].scale(radius));
                Vec3 b = center.add(UNIT_SPHERE[latitude + 1][longitude].scale(radius));
                Vec3 c = center.add(UNIT_SPHERE[latitude + 1][nextLongitude].scale(radius));
                Vec3 d = center.add(UNIT_SPHERE[latitude][nextLongitude].scale(radius));
                sphereVertex(buffer, matrix, a.subtract(camera), u0, v0, color);
                sphereVertex(buffer, matrix, b.subtract(camera), u0, v1, color);
                sphereVertex(buffer, matrix, c.subtract(camera), u1, v1, color);
                sphereVertex(buffer, matrix, a.subtract(camera), u0, v0, color);
                sphereVertex(buffer, matrix, c.subtract(camera), u1, v1, color);
                sphereVertex(buffer, matrix, d.subtract(camera), u1, v0, color);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void sphereVertex(com.mojang.blaze3d.vertex.BufferBuilder buffer,
            org.joml.Matrix4f matrix, Vec3 point, float u, float v, int color) {
        buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(color);
    }

    private static Vec3[][] createUnitSphere() {
        Vec3[][] vertices = new Vec3[SPHERE_LATITUDES + 1][SPHERE_LONGITUDES];
        for (int latitude = 0; latitude <= SPHERE_LATITUDES; latitude++) {
            double polar = Math.PI * latitude / SPHERE_LATITUDES;
            double y = Math.cos(polar);
            double ring = Math.sin(polar);
            for (int longitude = 0; longitude < SPHERE_LONGITUDES; longitude++) {
                double azimuth = Math.PI * 2.0 * longitude / SPHERE_LONGITUDES;
                vertices[latitude][longitude] = new Vec3(
                        ring * Math.cos(azimuth), y, ring * Math.sin(azimuth));
            }
        }
        return vertices;
    }

    private static void setShaderMode(float mode, float progress,
            boolean fadeFromNear, boolean widthAnimated) {
        shader.safeGetUniform("EffectMode").set(mode);
        shader.safeGetUniform("Progress").set(progress);
        shader.safeGetUniform("BeamFadeFromNear").set(fadeFromNear ? 1.0F : 0.0F);
        shader.safeGetUniform("BeamWidthAnimated").set(widthAnimated ? 1.0F : 0.0F);
    }

    private static void drawQuad(PoseStack poseStack, Vec3 camera,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int color) {
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = poseStack.last().pose();
        vertex(buffer, matrix, first.subtract(camera), 0.0F, 0.0F, color);
        vertex(buffer, matrix, second.subtract(camera), 0.0F, 1.0F, color);
        vertex(buffer, matrix, third.subtract(camera), 1.0F, 1.0F, color);
        vertex(buffer, matrix, fourth.subtract(camera), 1.0F, 0.0F, color);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer, org.joml.Matrix4f matrix,
            Vec3 point, float u, float v, int color) {
        buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(color);
    }

    private static boolean expired(Effect effect, long now) {
        DJRayEffectProfile profile = DJRayEffectLibrary.getInstance().resolve(effect.effect()).orElse(null);
        if (profile == null) {
            return true;
        }
        long lifetime = lifetime(profile, effect);
        return now - effect.createdAtMs() >= (effect.predicted() ? Math.max(lifetime, PREDICTION_RECONCILE_MS) : lifetime);
    }

    private static long lifetime(DJRayEffectProfile profile, Effect effect) {
        long lifetime = Math.max(profile.beamLifetimeMs(), profile.burstLifetimeMs());
        return effect.shockwaveRadius() > 0.0
                ? Math.max(lifetime, profile.shockwaveLifetimeMs()) : lifetime;
    }

    private static void add(Effect effect) {
        EFFECTS.addLast(effect);
        while (EFFECTS.size() > MAX_EFFECTS) {
            EFFECTS.removeFirst();
        }
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private record Effect(long sequence, UUID shooterId, InteractionHand hand, ResourceLocation effect,
            Vec3 origin, Vec3 end, List<Vec3> contacts, double shockwaveRadius,
            long createdAtMs, boolean predicted) {
        private Effect {
            contacts = List.copyOf(contacts);
        }
    }
}

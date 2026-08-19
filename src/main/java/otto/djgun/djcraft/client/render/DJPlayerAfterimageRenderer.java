package otto.djgun.djcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Captures and renders frozen, client-only snapshots of player base models.
 * Call {@link #emit} from any visual effect with its own color and lifetime.
 */
public final class DJPlayerAfterimageRenderer {
    private static final int MAX_AFTERIMAGES = 64;
    private static final long MIN_RENDER_AGE_MS = 16L;
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    private static final Deque<Afterimage> AFTERIMAGES = new ArrayDeque<>();

    private DJPlayerAfterimageRenderer() {
    }

    public static void emit(PlayerRenderer renderer, AbstractClientPlayer player, float partialTick,
            int argb, long lifetimeMs) {
        if (renderer == null || player == null || lifetimeMs <= 0L) {
            return;
        }

        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());
        if (player.isCrouching()) {
            y -= player.getScale() * 2.0F / 16.0F;
        }
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        ModelRotation rotation = captureModelRotation(player, partialTick);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        List<PartSnapshot> parts = trackedParts(model).stream().map(PartSnapshot::capture).toList();
        long createdAtMs = monotonicMs();
        AFTERIMAGES.addLast(new Afterimage(renderer, new Vec3(x, y, z), bodyYaw, rotation, player.getScale(),
                player.getSkin().texture(), parts, argb, createdAtMs, lifetimeMs));
        while (AFTERIMAGES.size() > MAX_AFTERIMAGES) {
            AFTERIMAGES.removeFirst();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || AFTERIMAGES.isEmpty()) {
            return;
        }
        long nowMs = monotonicMs();
        AFTERIMAGES.removeIf(afterimage -> nowMs - afterimage.createdAtMs() >= afterimage.lifetimeMs());
        if (AFTERIMAGES.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        for (Afterimage afterimage : AFTERIMAGES) {
            if (nowMs - afterimage.createdAtMs() >= MIN_RENDER_AGE_MS) {
                renderAfterimage(afterimage, nowMs, cameraPosition, poseStack, buffers);
            }
        }
        buffers.endBatch();
    }

    public static void clear() {
        AFTERIMAGES.clear();
    }

    private static void renderAfterimage(Afterimage afterimage, long nowMs, Vec3 cameraPosition,
            PoseStack poseStack, MultiBufferSource buffers) {
        PlayerModel<AbstractClientPlayer> model = afterimage.renderer().getModel();
        List<ModelPart> parts = trackedParts(model);
        if (parts.size() != afterimage.parts().size()) {
            return;
        }
        List<PartSnapshot> restore = parts.stream().map(PartSnapshot::capture).toList();
        try {
            for (int index = 0; index < parts.size(); index++) {
                afterimage.parts().get(index).apply(parts.get(index));
            }
            float ageProgress = Math.clamp(
                    (float) (nowMs - afterimage.createdAtMs()) / afterimage.lifetimeMs(), 0.0F, 1.0F);
            int color = multiplyAlpha(afterimage.argb(), 1.0F - ageProgress);
            Vec3 relative = afterimage.position().subtract(cameraPosition);

            poseStack.pushPose();
            try {
                poseStack.translate(relative.x, relative.y, relative.z);
                poseStack.scale(afterimage.entityScale(), afterimage.entityScale(), afterimage.entityScale());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - afterimage.bodyYaw()));
                if (afterimage.rotation().xDegrees() != 0.0F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(afterimage.rotation().xDegrees()));
                }
                if (afterimage.rotation().yRadians() != 0.0F) {
                    poseStack.mulPose(Axis.YP.rotation(afterimage.rotation().yRadians()));
                }
                if (afterimage.rotation().swimmingTranslation()) {
                    poseStack.translate(0.0F, -1.0F, 0.3F);
                }
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
                poseStack.translate(0.0F, -1.501F, 0.0F);
                VertexConsumer consumer =
                        buffers.getBuffer(RenderType.entityTranslucentEmissive(afterimage.texture()));
                model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, color);
            } finally {
                poseStack.popPose();
            }
        } finally {
            for (int index = 0; index < parts.size(); index++) {
                restore.get(index).apply(parts.get(index));
            }
        }
    }

    private static List<ModelPart> trackedParts(PlayerModel<AbstractClientPlayer> model) {
        return List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg, model.rightSleeve, model.leftSleeve,
                model.rightPants, model.leftPants, model.jacket);
    }

    private static ModelRotation captureModelRotation(AbstractClientPlayer player, float partialTick) {
        if (player.isFallFlying()) {
            float flyingTicks = player.getFallFlyingTicks() + partialTick;
            float flyingProgress = Mth.clamp(flyingTicks * flyingTicks / 100.0F, 0.0F, 1.0F);
            float xDegrees = player.isAutoSpinAttack()
                    ? 0.0F : flyingProgress * (-90.0F - player.getViewXRot(partialTick));
            Vec3 view = player.getViewVector(partialTick);
            Vec3 movement = player.getDeltaMovementLerped(partialTick);
            double movementHorizontal = movement.horizontalDistanceSqr();
            double viewHorizontal = view.horizontalDistanceSqr();
            float yRadians = 0.0F;
            if (movementHorizontal > 0.0 && viewHorizontal > 0.0) {
                double dot = (movement.x * view.x + movement.z * view.z)
                        / Math.sqrt(movementHorizontal * viewHorizontal);
                double cross = movement.x * view.z - movement.z * view.x;
                yRadians = (float) (Math.signum(cross) * Math.acos(dot));
            }
            return new ModelRotation(xDegrees, yRadians, false);
        }

        float swimAmount = player.getSwimAmount(partialTick);
        if (swimAmount > 0.0F) {
            boolean swimmingInFluid = player.isInWater()
                    || player.isInFluidType((fluidType, height) -> player.canSwimInFluidType(fluidType));
            float targetX = swimmingInFluid ? -90.0F - player.getXRot() : -90.0F;
            return new ModelRotation(Mth.lerp(swimAmount, 0.0F, targetX), 0.0F,
                    player.isVisuallySwimming());
        }
        return ModelRotation.NONE;
    }

    private static int multiplyAlpha(int argb, float multiplier) {
        int alpha = argb >>> 24;
        int fadedAlpha = Math.round(alpha * Math.clamp(multiplier, 0.0F, 1.0F));
        return argb & 0x00FFFFFF | fadedAlpha << 24;
    }

    private static long monotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private record Afterimage(PlayerRenderer renderer, Vec3 position, float bodyYaw, ModelRotation rotation,
            float entityScale,
            ResourceLocation texture, List<PartSnapshot> parts, int argb, long createdAtMs,
            long lifetimeMs) {
    }

    private record ModelRotation(float xDegrees, float yRadians, boolean swimmingTranslation) {
        private static final ModelRotation NONE = new ModelRotation(0.0F, 0.0F, false);
    }

    private record PartSnapshot(PartPose pose, float xScale, float yScale, float zScale,
            boolean visible, boolean skipDraw) {
        private static PartSnapshot capture(ModelPart part) {
            return new PartSnapshot(part.storePose(), part.xScale, part.yScale, part.zScale,
                    part.visible, part.skipDraw);
        }

        private void apply(ModelPart part) {
            part.loadPose(pose);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }
}

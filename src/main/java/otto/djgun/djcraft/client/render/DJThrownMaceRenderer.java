package otto.djgun.djcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import otto.djgun.djcraft.combat.DJMaceThrowRules;
import otto.djgun.djcraft.entity.DJThrownMace;

public final class DJThrownMaceRenderer extends EntityRenderer<DJThrownMace> {
    private final ItemRenderer itemRenderer;

    public DJThrownMaceRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DJThrownMace entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                (entity.tickCount + partialTick) * DJMaceThrowRules.ROTATION_DEGREES_PER_TICK));
        itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DJThrownMace entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}

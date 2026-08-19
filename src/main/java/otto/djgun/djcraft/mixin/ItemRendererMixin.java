package otto.djgun.djcraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.client.animation.DJAnimationHand;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Applies only the item-center layer after the model's first-person display transform. */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;",
                    shift = At.Shift.AFTER))
    private void djcraft$applyItemCenterPose(ItemStack stack, ItemDisplayContext displayContext,
            boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
            int combinedOverlay, BakedModel model, CallbackInfo callback) {
        if (!displayContext.firstPerson()) {
            return;
        }
        InteractionHand hand = djcraft$resolveHand(displayContext);
        if (hand == null) {
            return;
        }
        DJModeManagerClient.getInstance().getActiveSession().ifPresent(
                session -> DJAnimationRuntime.getInstance()
                        .applyItemCenterPose(hand, stack, poseStack, session));
    }

    @Unique
    private static InteractionHand djcraft$resolveHand(ItemDisplayContext displayContext) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        boolean renderedRightArm = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        DJAnimationHand animationHand = DJAnimationHand.fromPhysicalArm(
                renderedRightArm, player.getMainArm() == HumanoidArm.RIGHT);
        return animationHand == DJAnimationHand.MAIN
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

}

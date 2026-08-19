package otto.djgun.djcraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.client.DJShieldParryVisualState;
import otto.djgun.djcraft.client.animation.DJAnimationHand;
import otto.djgun.djcraft.client.render.DJParryShieldRenderer;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class BlockEntityWithoutLevelRendererMixin {
    @Shadow
    private ShieldModel shieldModel;

    @Inject(method = "renderByItem", at = @At("TAIL"))
    private void djcraft$renderParryShieldGlow(ItemStack stack, ItemDisplayContext displayContext,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
            CallbackInfo callback) {
        if (!displayContext.firstPerson() || !DJItemBehaviorManager.is(stack, DJItemBehavior.SHIELD)
                || !DJParryShieldRenderer.isReady() || shieldModel == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        DJAnimationHand animationHand = DJAnimationHand.fromPhysicalArm(
                displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                player.getMainArm() == HumanoidArm.RIGHT);
        InteractionHand hand = animationHand == DJAnimationHand.MAIN
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null || !DJShieldParryVisualState.isActive(
                session.getSessionId(), hand, session.getCurrentTimeMs())) {
            return;
        }
        DJParryShieldRenderer.renderShield(shieldModel, stack, poseStack, bufferSource);
    }
}

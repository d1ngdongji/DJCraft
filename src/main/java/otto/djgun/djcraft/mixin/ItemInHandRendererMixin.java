package otto.djgun.djcraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.animation.DJAnimationSwitchRules;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

/** Lets the DJ animator exclusively own first-person attack and equip motion while a session is active. */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void djcraft$scheduleSwitchBeforeVanillaCacheWrite(CallbackInfo ci) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        var player = Minecraft.getInstance().player;
        if (session == null || player == null) {
            return;
        }
        ItemStack selectedMainStack = player.getMainHandItem();
        ItemStack selectedOffStack = player.getOffhandItem();
        DJAnimationRuntime runtime = DJAnimationRuntime.getInstance();
        int mainDurationBeats = djcraft$switchDuration(mainHandItem, selectedMainStack);
        int offDurationBeats = djcraft$switchDuration(offHandItem, selectedOffStack);
        if (runtime.updateHandSwap(mainHandItem, offHandItem, selectedMainStack, selectedOffStack,
                session, mainDurationBeats, offDurationBeats)) {
            return;
        }
        if (DJAnimationSwitchRules.isInstantRemoval(player, mainHandItem, selectedMainStack)) {
            mainHandItem = selectedMainStack;
            runtime.observeRenderedStack(InteractionHand.MAIN_HAND, selectedMainStack);
        } else {
            djcraft$scheduleSwitch(
                    InteractionHand.MAIN_HAND, mainHandItem, selectedMainStack, player, session);
        }

        if (DJAnimationSwitchRules.isInstantRemoval(player, offHandItem, selectedOffStack)) {
            offHandItem = selectedOffStack;
            runtime.observeRenderedStack(InteractionHand.OFF_HAND, selectedOffStack);
        } else {
            djcraft$scheduleSwitch(
                    InteractionHand.OFF_HAND, offHandItem, selectedOffStack, player, session);
        }
    }

    @Unique
    private static int djcraft$switchDuration(ItemStack cachedStack, ItemStack selectedStack) {
        return selectedStack.isEmpty()
                ? DJItemCooldownManager.getSwitchWarmup(cachedStack)
                : DJItemCooldownManager.getSwitchWarmup(selectedStack);
    }

    @Unique
    private static void djcraft$scheduleSwitch(InteractionHand hand, ItemStack cachedStack, ItemStack selectedStack,
            Player player, DJSessionClient session) {
        Item cachedItem = cachedStack.isEmpty() ? null : cachedStack.getItem();
        Item selectedItem = selectedStack.isEmpty() ? null : selectedStack.getItem();
        if (cachedItem == selectedItem) {
            return;
        }
        if (DJAnimationSwitchRules.isInstantRemoval(player, cachedStack, selectedStack)) {
            return;
        }

        int durationBeats = selectedStack.isEmpty()
                ? DJItemCooldownManager.getSwitchWarmup(cachedStack)
                : DJItemCooldownManager.getSwitchWarmup(selectedStack);
        if (durationBeats <= 0) {
            return;
        }

        DJAnimationRuntime runtime = DJAnimationRuntime.getInstance();
        runtime.observeRenderedStack(hand, cachedStack);
        runtime.scheduleSwitch(hand, cachedStack, selectedStack, session, durationBeats);
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float djcraft$suppressVanillaSwing(float swingProgress) {
        return DJModeManagerClient.getInstance().isInDJMode() ? 0.0f : swingProgress;
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float djcraft$suppressVanillaEquip(float equippedProgress) {
        return DJModeManagerClient.getInstance().isInDJMode() ? 0.0f : equippedProgress;
    }

    @Inject(method = "renderArmWithItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE))
    private void djcraft$applyHandSpacePose(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress,
            PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        DJModeManagerClient.getInstance().getActiveSession().ifPresent(session ->
                DJAnimationRuntime.getInstance().beginFirstPersonRender(hand, stack, poseStack, session));
    }

    @Redirect(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;mainHandItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = Opcodes.PUTFIELD))
    private void djcraft$delayMainHandItemHandoff(ItemInHandRenderer renderer, ItemStack nextStack) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session != null) {
            DJAnimationRuntime runtime = DJAnimationRuntime.getInstance();
            if (runtime.shouldHoldHandSwap(InteractionHand.MAIN_HAND, mainHandItem, nextStack)
                    || runtime.shouldHoldOutgoing(
                            InteractionHand.MAIN_HAND, mainHandItem, nextStack, session)) {
                return;
            }
        }
        mainHandItem = nextStack;
        if (session != null) {
            DJAnimationRuntime.getInstance().onCachedItemAssigned(
                    InteractionHand.MAIN_HAND, nextStack, session);
        }
    }

    @Redirect(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;offHandItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = Opcodes.PUTFIELD))
    private void djcraft$delayOffHandItemHandoff(ItemInHandRenderer renderer, ItemStack nextStack) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session != null) {
            DJAnimationRuntime runtime = DJAnimationRuntime.getInstance();
            if (runtime.shouldHoldHandSwap(InteractionHand.OFF_HAND, offHandItem, nextStack)
                    || runtime.shouldHoldOutgoing(
                            InteractionHand.OFF_HAND, offHandItem, nextStack, session)) {
                return;
            }
        }
        offHandItem = nextStack;
        if (session != null) {
            DJAnimationRuntime.getInstance().onCachedItemAssigned(
                    InteractionHand.OFF_HAND, nextStack, session);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void djcraft$commitScheduledHandHandoffs(CallbackInfo ci) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        var player = Minecraft.getInstance().player;
        if (session == null || player == null) {
            return;
        }
        DJAnimationRuntime runtime = DJAnimationRuntime.getInstance();
        ItemStack selectedMainStack = player.getMainHandItem();
        ItemStack selectedOffStack = player.getOffhandItem();
        if (runtime.shouldCommitHandSwap(
                mainHandItem, offHandItem, selectedMainStack, selectedOffStack, session)) {
            mainHandItem = selectedMainStack;
            offHandItem = selectedOffStack;
            runtime.onHandSwapCachedItemsAssigned(selectedMainStack, selectedOffStack, session);
            return;
        }
        if (runtime.hasActiveHandSwap()) {
            return;
        }
        if (runtime.shouldCommitHandoff(
                InteractionHand.MAIN_HAND, mainHandItem, selectedMainStack, session)) {
            mainHandItem = selectedMainStack;
            runtime.onCachedItemAssigned(InteractionHand.MAIN_HAND, selectedMainStack, session);
        }
        if (runtime.shouldCommitHandoff(
                InteractionHand.OFF_HAND, offHandItem, selectedOffStack, session)) {
            offHandItem = selectedOffStack;
            runtime.onCachedItemAssigned(InteractionHand.OFF_HAND, selectedOffStack, session);
        }
    }
}

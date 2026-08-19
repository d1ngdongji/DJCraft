package otto.djgun.djcraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

/**
 * Mixin 覆盖 CrossbowItem.isCharged(ItemStack)
 *
 * 在 DJ 模式下，弩始终显示为"已装弹"状态，但当持有弩的玩家处于 DJ 射击冷却期间
 * 时，返回 false（显示为未装弩），作为"正在装弹"的视觉反馈。
 *
 * 实际的装填与发射由通用触发型客户端处理器和 DJNetwork（服务端）共同处理，
 * 不依赖于 CHARGED_PROJECTILES 数据组件的实际状态。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {

    /**
     * 覆盖 isCharged 的返回值：
     * - DJ 模式 + 玩家不在冷却中 → true（显示已装弩）
     * - DJ 模式 + 玩家在冷却中 → false（显示装弩中）
     * - 非 DJ 模式 → 原版逻辑
     */
    @Inject(method = "isCharged", at = @At("RETURN"), cancellable = true)
    private static void djIsCharged(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!DJModeManagerClient.getInstance().isInDJMode())
            return;
        if (!DJItemBehaviorManager.is(stack, DJItemBehavior.CROSSBOW))
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // 冷却中 → 未装弩（视觉反馈）；否则 → 已装弩
        boolean onCooldown = mc.player.getCooldowns().isOnCooldown(stack.getItem());
        cir.setReturnValue(!onCooldown);
    }
}

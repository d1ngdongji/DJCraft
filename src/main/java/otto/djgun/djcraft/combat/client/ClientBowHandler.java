package otto.djgun.djcraft.combat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.session.DJModeManagerClient;

/**
 * 客户端弓 DJ 集成处理器（按下拦截部分）。
 *
 * <p>
 * 在 DJ 模式下，拦截玩家按下右键开始拉弓的动作，进行节拍判定：
 * <ul>
 * <li>命中（Hit）→ 允许开始拉弦（原版流程继续）</li>
 * <li>失误（Miss）→ 发送 proof；不卡拍伤害为 0 时取消并施加惩罚，否则允许拉弦</li>
 * </ul>
 *
 * <p>
 * 前置守卫逻辑委托给 {@link DJClientEventContext#resolve}，
 * 武器差异逻辑委托给 {@link DJChargeWeaponHelper}。
 *
 * <p>
 * 松开时的节拍判定由 {@link LivingEntityUseItemEvent.Stop} 在客户端与服务端共同处理，
 * 因而覆盖未调用 {@code super.releaseUsing(...)} 的弓子类。
 *
 * <p>
 * 注册方式：在 {@code DJCraftClient} 构造函数中注册到 {@code NeoForge.EVENT_BUS}。
 */
@OnlyIn(Dist.CLIENT)
public class ClientBowHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        var ctx = DJClientEventContext.resolveCharge(event);
        if (ctx.isEmpty())
            return;

        var c = ctx.get();

        // 如果玩家已经在拉弓（isUsingItem），不干预（持续按住，不是新按下）
        if (c.player().isUsingItem())
            return;

        // 委托给通用蓄力型武器处理器
        DJChargeWeaponHelper.handlePress(event, c.player(), c.stack(), c.hand(), c.session());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!event.getEntity().level().isClientSide()
                || !DJItemBehaviorManager.resolve(event.getItem()).isCharge()) {
            return;
        }
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session != null && !DJChargeWeaponHelper.handleRelease(event.getItem(), event.getEntity(), session)) {
            event.setCanceled(true);
        }
    }
}

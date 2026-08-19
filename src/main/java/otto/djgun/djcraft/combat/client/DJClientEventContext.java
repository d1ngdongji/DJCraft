package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJActionSource;

import java.util.Optional;

/**
 * 客户端武器 DJ 处理器的统一前置上下文。
 *
 * <p>
 * 将所有武器处理器中重复的前置守卫逻辑（isUseItem 早退、空玩家检测、
 * DJ 模式 + session 播放判定、手持物获取）统一封装，
 * 各 Handler 仅关注武器差异逻辑。
 *
 * <p>
 * 使用 {@link #resolve(InputEvent.InteractionKeyMappingTriggered, Class)} 解析，
 * 返回 {@link Optional} 为空表示不满足前置条件，Handler 应直接返回。
 */
@OnlyIn(Dist.CLIENT)
public record DJClientEventContext(Player player, InteractionHand hand, ItemStack stack, DJActionSource source,
        DJSessionClient session) {

    /**
     * 尝试解析当前交互事件为 DJ 武器上下文。
     *
     * @param event    原始交互事件
     * @param itemType 期望的武器 Item 类型
     * @param <T>      Item 子类型
     * @return 非空 Optional 表示条件全部满足；空 Optional 表示应提前退出
     */
    public static <T extends Item> Optional<DJClientEventContext> resolve(
            InputEvent.InteractionKeyMappingTriggered event,
            Class<T> itemType) {
        return resolve(event, stack -> itemType.isInstance(stack.getItem()));
    }

    public static Optional<DJClientEventContext> resolve(
            InputEvent.InteractionKeyMappingTriggered event,
            DJItemBehavior behavior) {
        return resolve(event, stack -> DJItemBehaviorManager.is(stack, behavior));
    }

    public static Optional<DJClientEventContext> resolveCharge(
            InputEvent.InteractionKeyMappingTriggered event) {
        return resolve(event, stack -> DJItemBehaviorManager.resolve(stack).isCharge());
    }

    public static Optional<DJClientEventContext> resolveTrigger(
            InputEvent.InteractionKeyMappingTriggered event) {
        return resolve(event, stack -> DJItemBehaviorManager.resolve(stack).isTrigger());
    }

    private static Optional<DJClientEventContext> resolve(
            InputEvent.InteractionKeyMappingTriggered event,
            java.util.function.Predicate<ItemStack> itemMatcher) {
        if (!event.isUseItem())
            return Optional.empty();

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return Optional.empty();

        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null)
            return Optional.empty();

        InteractionHand hand = event.getHand();
        if (hand == null)
            return Optional.empty();
        var captured = DJClientActionCapture.consumeUseIf(player, hand, itemMatcher);
        if (captured == null) {
            return Optional.empty();
        }
        ItemStack stack = captured.stack();

        return Optional.of(new DJClientEventContext(player, hand, stack, captured.source(), session));
    }
}

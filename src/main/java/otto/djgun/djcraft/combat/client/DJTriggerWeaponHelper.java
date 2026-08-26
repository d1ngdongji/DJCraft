package otto.djgun.djcraft.combat.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import otto.djgun.djcraft.combat.client.BeatJudgeFacade;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.sound.DJActionOutcome;

import java.util.function.Consumer;

/**
 * 触发型武器（弩类）在 DJ 模式下的通用处理逻辑。
 *
 * <p>
 * 适用于"按下右键即触发一次"的武器，例如弩、自定义触发型武器等。
 *
 * <p>
 * 完整接管右键事件：
 * <ul>
 * <li>处于冷却中 → 静默取消，不做任何事</li>
 * <li>完成判定后调用 {@code onJudged}，由服务端按 gamerule 决定是否发射</li>
 * <li>节拍失误（Miss）→ 同样发送 proof 并施加冷却（防止刷判定）</li>
 * </ul>
 *
 * <p>
 * 冷却时长从 {@link DJItemCooldownManager#getUseBeatCooldown(ItemStack)} 读取，
 * 支持通过服务端同步的数据包 Profile 覆盖（内置弩 Profile 为 4 拍）。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * DJTriggerWeaponHelper.handlePress(event, player, stack, hand, session,
 *         result -> PacketDistributor.sendToServer(new MyFirePayload(
 *                 DJClientJudgmentProofs.create(context.session(), result), hand)));
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class DJTriggerWeaponHelper {

    private DJTriggerWeaponHelper() {
    }

    /**
     * 处理触发型武器的右键按下事件。
     *
     * @param event   原始交互事件（会被强制 cancel）
     * @param player  玩家
     * @param stack   该手持有的物品栈
     * @param hand    触发手（传入 onHit 回调供使用）
     * @param session 当前 DJ 会话
     * @param onJudged 完成判定后的回调，接收 {@link HitResult}（含节拍类别）
     */
    public static void handlePress(
            InputEvent.InteractionKeyMappingTriggered event,
            Player player,
            ItemStack stack,
            InteractionHand hand,
            DJSessionClient session,
            Consumer<HitResult> onJudged) {
        if (session.getEnergy() < DJItemCooldownManager.getUseEnergyCost(stack)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        // 完全接管右键行为，禁止原版交互逻辑
        event.setCanceled(true);
        event.setSwingHand(false);

        // 处于冷却中：静默取消，不做任何事
        if (player.getCooldowns().isOnCooldown(stack.getItem()))
            return;

        // 节拍判定 + 即时视觉反馈（统一门面）
        HitResult result = BeatJudgeFacade.judgeAttackAndNotify(session, stack);

        onJudged.accept(result);

        // 命中与失误均施加冷却，防止玩家反复刷判定
        applyCooldown(player, stack, hand, session, result);
    }

    /**
     * 根据 {@link DJItemCooldownManager#getUseBeatCooldown(ItemStack)} 施加客户端冷却。
     * 采用 -0.4 拍宽容逻辑，与近战武器保持一致。
     */
    public static void applyCooldown(Player player, ItemStack stack, InteractionHand hand, DJSessionClient session,
            HitResult result) {
        int beats = DJItemCooldownManager.getUseBeatCooldown(stack);
        applyBeats(player, stack, hand, session, beats - 0.4, beats, result, true);
    }

    /**
     * 施加任意拍数的 DJ 冷却，并单独指定不扣除宽容值的动画拍数。
     * 供 {@link DJChargeWeaponHelper} 和 {@link ClientShieldHandler} 复用。
     */
    public static void applyBeats(Player player, ItemStack stack, InteractionHand hand, DJSessionClient session,
            double cooldownBeats, double animationBeats) {
        applyBeats(player, stack, hand, session, cooldownBeats, animationBeats, true);
    }

    public static void applyBeats(Player player, ItemStack stack, InteractionHand hand, DJSessionClient session,
            double cooldownBeats, double animationBeats, boolean successful) {
        DJActionOutcome outcome = successful ? DJActionOutcome.NOT_JUDGED
                : new DJActionOutcome(otto.djgun.djcraft.sound.BeatOutcome.MISS,
                        otto.djgun.djcraft.sound.TargetOutcome.NOT_APPLICABLE);
        DJAnimationRuntime.getInstance().emit(
                DJAnimationEvent.Kind.TRIGGER_IMPACT, hand, stack, session, animationBeats, outcome);
        applyCooldownTicks(player, stack, session, cooldownBeats);
    }

    public static void applyBeats(Player player, ItemStack stack, InteractionHand hand, DJSessionClient session,
            double cooldownBeats, double animationBeats, HitResult result, boolean canHitTarget) {
        DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.TRIGGER_IMPACT, hand, stack, session,
                animationBeats, DJActionOutcome.judged(result, canHitTarget), 0L,
                result.judgedAtMs(), result.beatIndex());
        applyCooldownTicks(player, stack, session, cooldownBeats);
    }

    private static void applyCooldownTicks(Player player, ItemStack stack, DJSessionClient session,
            double cooldownBeats) {
        DJClientItemCooldowns.apply(player, stack, session, cooldownBeats);
    }

    public static void applyCooldownBeats(Player player, ItemStack stack, DJSessionClient session,
            double cooldownBeats) {
        applyCooldownTicks(player, stack, session, cooldownBeats);
    }
}

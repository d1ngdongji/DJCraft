package otto.djgun.djcraft.combat.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.client.BeatJudgeFacade;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.sound.DJActionOutcome;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.network.packet.DJChargeStartPayload;

/**
 * 蓄力型武器（弓类）在 DJ 模式下的通用处理逻辑。
 *
 * <p>
 * 适用于"长按右键蓄力、松开右键射击"的武器，例如弓、自定义蓄力型武器等。
 *
 * <h3>阶段1 — 按下右键（开始蓄力）：{@link #handlePress}</h3>
 * <ul>
 * <li>处于冷却中（按下失误的惩罚冷却）→ 静默取消，不允许开始蓄力</li>
 * <li>节拍命中（Hit）→ 允许原版蓄力继续</li>
 * <li>节拍失误（Miss）→ 发送独立 proof；不卡拍伤害为 0 时取消并施加惩罚，否则继续蓄力</li>
 * </ul>
 *
 * <h3>阶段2 — 松开右键（射出）：{@link #handleRelease}（仅客户端 Mixin 调用）</h3>
 * <ul>
 * <li>进行节拍判定并发送 DJChargeReleasePayload 到服务端（服务端 Mixin 据此决定是否生成箭矢实体）</li>
 * <li>命中或服务端同步规则允许的 Miss → 返回 {@code true}，客户端放行箭矢预测</li>
 * <li>规则为 0 的 Miss → 返回 {@code false}，调用方取消释放</li>
 * </ul>
 *
 * <p>
 * <b>关于蓄力速度：</b>蓄力型武器的"冷却节拍数"用于控制蓄力速度（即达到满力所需的拍数），
 * 而非松开后施加的额外冷却。具体的蓄力速度缩放逻辑因武器而异（每种武器在自己的 Mixin
 * 中读取
 * {@link otto.djgun.djcraft.combat.DJItemCooldownManager#getUseBeatCooldown(ItemStack)}
 * 并映射为蓄力时长），无法在本通用类中泛化实现，因此不在此处处理。
 */
@OnlyIn(Dist.CLIENT)
public final class DJChargeWeaponHelper {

    /** 按下判定失败的惩罚冷却拍数（固定 1 拍，适用于所有蓄力型武器） */
    private static final int PRESS_MISS_PENALTY_BEATS = 1;

    private DJChargeWeaponHelper() {
    }

    /**
     * 处理蓄力型武器的按下右键（开始蓄力）事件。
     *
     * @param event   原始交互事件（失误时会被 cancel）
     * @param player  玩家
     * @param stack   该手持有的物品栈
     * @param hand    触发手（保留备扩展，暂未使用）
     * @param session 当前 DJ 会话
     */
    public static void handlePress(
            InputEvent.InteractionKeyMappingTriggered event,
            Player player,
            ItemStack stack,
            InteractionHand hand,
            DJSessionClient session) {

        // 处于惩罚冷却中：静默取消，不允许开始蓄力
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (session.getEnergy() < DJItemCooldownManager.getUseEnergyCost(stack)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        // 节拍判定 + 即时视觉反馈（统一门面）
        var result = BeatJudgeFacade.judgeAttackAndNotify(session, stack);
        var proof = DJClientJudgmentProofs.create(session, result);
        PacketDistributor.sendToServer(new DJChargeStartPayload(proof, hand,
                DJActionSource.capture(player, hand, stack)));

        if (!result.isHit() && !session.allowsOffBeatAttack()) {
            // 失误：取消开始蓄力，施加 1 拍惩罚冷却（-0.4 宽容）
            event.setCanceled(true);
            event.setSwingHand(false);
            DJTriggerWeaponHelper.applyBeats(player, stack, hand, session,
                    PRESS_MISS_PENALTY_BEATS - 0.4, PRESS_MISS_PENALTY_BEATS, result, false);
        } else {
            DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.CHARGE_START, hand, stack, session,
                    DJItemCooldownManager.getUseBeatCooldown(stack), DJActionOutcome.judged(result, false),
                    proof.actionSequence(), result.judgedAtMs(), result.beatIndex());
        }
        // 命中：放行原版逻辑，允许玩家开始蓄力
    }

    /**
     * 处理蓄力型武器的松开右键（射出）事件（仅客户端 Mixin 中调用）。
     *
     * <p>
     * 由于判定逻辑和网络发包已在 {@code MultiPlayerGameModeMixin} 中提前完成
     * （为了确保服务端的包接收顺序），此方法仅读取预计算的结果。
     *
     * <p>
     * 松开后<b>不施加任何额外冷却</b>：蓄力型武器的冷却期体现为蓄力速度，
     * 而非发射后的等待时间（蓄力速度缩放由各武器的 Mixin 自行实现）。
     *
     * @param stack   持有的物品栈
     * @param entity  使用物品的实体
     * @param session 当前 DJ 会话
     * @return {@code true} 表示命中（客户端允许箭矢预测），
     *         {@code false} 表示失误（调用方应对 CallbackInfo 执行 cancel）
     */
    public static boolean handleRelease(ItemStack stack, LivingEntity entity, DJSessionClient session) {
        // 读取由 MultiPlayerGameModeMixin 提前判定并存储的结果
        // gameTime 从 entity.level() 获取，避免在 DJChargeReleaseState 中引入客户端专有 API
        long gameTime = entity.level().getGameTime();
        DJChargeReleaseState.PendingRelease release = DJChargeReleaseState.consume(
                entity.getUsedItemHand(), stack.getItem(), 10, gameTime);

        if (release == null) {
            // 没有预存状态：降级放行（不静默拦截），并记录警告便于排查
            DJCraft.LOGGER.warn("DJChargeWeaponHelper: no pending release state found " +
                    "(hand={}, item={}). Allowing passthrough to avoid incorrect cancellation.",
                    entity.getUsedItemHand(), stack.getItem());
            return true;
        }

        DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.CHARGE_RELEASE,
                entity.getUsedItemHand(), stack, session, 0.45,
                DJActionOutcome.judged(release.result(), true), release.actionSequence(),
                release.result().judgedAtMs(), release.result().beatIndex());

        // 不在此处施加冷却：蓄力速度由各武器 Mixin 中的 getPowerForTime 覆盖控制
        return release.result().isHit() || session.allowsOffBeatAttack();
    }
}

package otto.djgun.djcraft.combat.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.HitResult;

/**
 * 客户端蓄力型武器松开判定结果的临时存储。
 *
 * <p>
 * 由 {@code MultiPlayerGameModeMixin} 在 {@code releaseUsingItem} 最开始（原版
 * 数据包发出之前）写入，由 {@code DJChargeWeaponHelper.handleRelease} 在随后的
 * {@code BowItemMixin} 中读取消费。
 *
 * <p>
 * 结构化状态携带 hand / item / gameTime，允许 consume 时进行匹配校验，
 * 避免单一布尔值无法追踪来源的问题。
 *
 * <p>
 * 注意：本类不得引用任何 Minecraft 客户端专有类（如 {@code Minecraft}、{@code LocalPlayer}），
 * 否则服务端的 RuntimeDistCleaner 在处理字节码时会尝试解析这些类并引发崩溃。
 */
@OnlyIn(Dist.CLIENT)
public final class DJChargeReleaseState {

    private DJChargeReleaseState() {
    }

    /**
     * 结构化的待消费释放判定结果。
     *
     * @param result   完整节拍判定结果
     * @param actionSequence 对应的战斗动作序号
     * @param hand     产生此次释放的交互手
     * @param gameTime 写入时的游戏 Tick（用于超时检测）
     * @param item     写入时的武器 Item 类型
     */
    public record PendingRelease(HitResult result, long actionSequence, InteractionHand hand, long gameTime, Item item) {
    }

    /** 预计算的判定结果；null 表示当前不在 DJ 蓄力释放流程中 */
    private static volatile PendingRelease pending = null;

    /**
     * 由 MultiPlayerGameModeMixin 在发包前写入完整上下文。
     *
     * @param result   判定结果
     * @param actionSequence 对应的战斗动作序号
     * @param hand     触发的交互手
     * @param gameTime 当前游戏 Tick（来自 {@code level.getGameTime()}）
     * @param item     正在使用的武器 Item
     */
    public static void store(HitResult result, long actionSequence, InteractionHand hand, long gameTime, Item item) {
        pending = new PendingRelease(result, actionSequence, hand, gameTime, item);
    }

    public static void clear() {
        pending = null;
    }

    /**
     * 消费并清除存储的结果，带上下文校验。
     *
     * <p>
     * 当 pending 为 null、手/物品不匹配或已超时时，返回 {@code null} 并记录警告。
     * 调用方须自行决定在 null 情况下是放行还是拦截。
     *
     * @param hand        消费方期望的交互手
     * @param item        消费方期望的武器 Item
     * @param maxAgeTicks 最大允许年龄（Tick），超过则视为过期
     * @param gameTime    消费时的游戏 Tick（从 {@code entity.level().getGameTime()} 传入）
     * @return 有效的待释放上下文，或 null（无有效状态/上下文不匹配）
     */
    public static PendingRelease consume(InteractionHand hand, Item item, int maxAgeTicks, long gameTime) {
        PendingRelease p = pending;
        pending = null;

        if (p == null) {
            // 无预存状态——某些情况下（非正常 DJ 流程）会触发，不视为错误
            return null;
        }

        if (p.hand() != hand || p.item() != item) {
            DJCraft.LOGGER.warn(
                    "DJChargeReleaseState: consume context mismatch. " +
                    "Expected hand={}, item={}; stored hand={}, item={}. Discarding.",
                    hand, item, p.hand(), p.item());
            return null;
        }

        if (gameTime - p.gameTime() > maxAgeTicks) {
            DJCraft.LOGGER.warn(
                    "DJChargeReleaseState: pending release expired (age={} ticks, max={}). Discarding.",
                    gameTime - p.gameTime(), maxAgeTicks);
            return null;
        }

        return p;
    }
}

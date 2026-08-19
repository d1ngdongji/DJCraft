package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.combat.BeatJudgmentEvaluator;
import otto.djgun.djcraft.combat.DJBeatDamageRules;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJMeleeBehaviorResolver;
import otto.djgun.djcraft.api.combat.DJItemBehaviorDefinition;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.hud.DJBeatHudRenderer;
import otto.djgun.djcraft.session.DJSessionClient;

/**
 * 节拍判定门面：将 {@link BeatJudgeUtil#judge} 与客户端 HUD 判定反馈
 * 合并为单次调用，避免各调用点独立组合时出现"判定后忘记视觉反馈"或"反馈时间戳不一致"的隐患。
 *
 * <p>
 * 必须放在 {@code combat/client/} 包而非 {@code combat/} 包，
 * 因为本类引用了客户端专有类 {@link DJCrosshairRenderer}，
 * 不能出现在服务端的类加载路径中。
 */
@OnlyIn(Dist.CLIENT)
public final class BeatJudgeFacade {

    private BeatJudgeFacade() {
    }

    /**
     * 对当前 DJ 会话执行节拍判定，并立即触发准星视觉反馈。
     *
     * @param session 当前客户端 DJ 会话
     * @return 判定结果（含 isHit、beatEvent 和节拍类别）
     */
    public static HitResult judgeAndNotify(DJSessionClient session) {
        return judgeAndNotify(session, DJItemBehaviorRegistry.MELEE);
    }

    public static HitResult judgeAndNotify(DJSessionClient session, DJItemBehaviorDefinition behavior) {
        return judgeAndNotify(session, behavior.disabledByCanAttack());
    }

    private static HitResult judgeAndNotify(DJSessionClient session, boolean disabledByCanAttack) {
        long judgedAtMs = session.getCurrentTimeMs();
        HitResult result = BeatJudgmentEvaluator.evaluate(
                judgedAtMs, session.getTrackPack(), disabledByCanAttack);
        DJBeatHudRenderer.notifyJudgment(session, result, false);
        return result;
    }

    /** Performs an offensive judgment; misses also break the current combo. */
    public static HitResult judgeAttackAndNotify(DJSessionClient session) {
        var player = Minecraft.getInstance().player;
        return judgeAttackAndNotify(
                session, player == null ? ItemStack.EMPTY : player.getMainHandItem());
    }

    /**
     * Performs an offensive judgment using the exact action stack for category-match visuals.
     */
    public static HitResult judgeAttackAndNotify(DJSessionClient session, ItemStack actionStack) {
        DJItemBehaviorDefinition behavior = DJItemBehaviorManager.resolveDefinition(actionStack);
        return judgeAttackAndNotify(session, actionStack, behavior);
    }

    private static HitResult judgeAttackAndNotify(DJSessionClient session, ItemStack actionStack,
            DJItemBehaviorDefinition behavior) {
        long judgedAtMs = session.getCurrentTimeMs();
        HitResult result = BeatJudgmentEvaluator.evaluate(
                judgedAtMs, session.getTrackPack(), behavior.disabledByCanAttack());
        return notifyAttackJudgment(session, actionStack, result);
    }

    /** Performs an ordinary melee judgment using the registered melee action policy. */
    public static HitResult judgeMeleeAttackAndNotify(DJSessionClient session, ItemStack actionStack) {
        return judgeAttackAndNotify(session, actionStack,
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(actionStack));
    }

    private static HitResult notifyAttackJudgment(DJSessionClient session, ItemStack actionStack, HitResult result) {
        boolean categoryMatched = result.isHit()
                && DJBeatDamageRules.isCategoryMatched(result.beatData(), actionStack);
        DJBeatHudRenderer.notifyJudgment(session, result, categoryMatched);
        return result;
    }
}

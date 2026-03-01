package otto.djgun.djcraft.combat;

import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.session.DJSessionClient;

import java.util.List;

/**
 * 节拍判定器
 * 提供客户端 (DJSessionClient) 和服务端 (DJSession) 两个入口，共享内部逻辑
 */
public class BeatJudgeUtil {

    /** 服务端入口：使用 DJSession（系统时间）进行节拍判定 */
    public static HitResult judge(DJSession session) {
        if (session == null || !session.isPlaying())
            return HitResult.miss();
        return judgeInternal(
                session.getCurrentTimeMs(),
                session.getTrackPack().timeline().combatLine(),
                session.getTrackPack());
    }

    /** 客户端入口：使用 DJSessionClient（OpenAL 高精度时间）进行节拍判定 */
    public static HitResult judge(DJSessionClient session) {
        if (session == null || !session.isPlaying())
            return HitResult.miss();
        return judgeInternal(
                session.getCurrentTimeMs(),
                session.getTrackPack().timeline().combatLine(),
                session.getTrackPack());
    }

    /**
     * 内部判定逻辑，客户端和服务端共用
     */
    private static HitResult judgeInternal(long currentTimeMs, List<BeatEvent> combatLine, TrackPack trackPack) {
        if (combatLine == null || combatLine.isEmpty())
            return HitResult.miss();

        // 二分搜索：找到 t <= currentTimeMs 的最后一个节拍的索引 (floorIndex)
        // 不依赖 lastBeatIndex，完全基于真实时间，绕开 20 TPS 的刷新粒度问题
        int lo = 0, hi = combatLine.size() - 1, floorIndex = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (combatLine.get(mid).t() <= currentTimeMs) {
                floorIndex = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        // prevBeat = 已过去的最近拍，nextBeat = 即将到来的最近拍
        BeatEvent prevBeat = (floorIndex >= 0) ? combatLine.get(floorIndex) : null;
        BeatEvent nextBeat = (floorIndex + 1 < combatLine.size()) ? combatLine.get(floorIndex + 1) : null;
        int prevIndex = floorIndex;
        int nextIndex = floorIndex + 1;

        if (prevBeat == null && nextBeat == null)
            return HitResult.miss();

        // 找绝对距离最近的节拍
        long diffToPrev = prevBeat != null ? Math.abs(currentTimeMs - prevBeat.t()) : Long.MAX_VALUE;
        long diffToNext = nextBeat != null ? Math.abs(currentTimeMs - nextBeat.t()) : Long.MAX_VALUE;

        BeatEvent closestBeat;
        long minDiff;
        int closestIndex;

        if (diffToPrev <= diffToNext) {
            closestBeat = prevBeat;
            closestIndex = prevIndex;
            minDiff = diffToPrev;
        } else {
            closestBeat = nextBeat;
            closestIndex = nextIndex;
            minDiff = diffToNext;
        }

        // 获取节拍定义（支持通过 BeatEvent.props 覆盖，未来扩展用此处理）
        BeatDefinition definition = trackPack.getDefinition(closestBeat.type());
        if (definition == null) {
            definition = BeatDefinition.createDefault();
        }

        // 如果该节拍不允许攻击
        if (!definition.canAttack()) {
            return HitResult.miss(definition, closestBeat);
        }

        // 计算容忍度阈值（毫秒）
        // tolerance > 1.0 → 整数，视为绝对毫秒数
        // tolerance <= 1.0 → 百分比：提前按=到前一拍的间隔比例，晚按=到后一拍的间隔比例
        long toleranceMs;
        float toleranceProp = definition.tolerance();

        if (toleranceProp > 1.0f) {
            toleranceMs = (long) toleranceProp;
        } else {
            long referenceInterval;
            if (currentTimeMs < closestBeat.t()) {
                // 提前按（还没到拍点）：使用该拍到前一拍的间隔
                if (closestIndex > 0) {
                    referenceInterval = closestBeat.t() - combatLine.get(closestIndex - 1).t();
                } else {
                    // 没有前一拍（首拍），回退用后一拍的间隔
                    referenceInterval = closestIndex < combatLine.size() - 1
                            ? combatLine.get(closestIndex + 1).t() - closestBeat.t()
                            : 500;
                }
            } else {
                // 晚按（已过拍点）：使用该拍到后一拍的间隔
                if (closestIndex < combatLine.size() - 1) {
                    referenceInterval = combatLine.get(closestIndex + 1).t() - closestBeat.t();
                } else {
                    // 没有后一拍（末拍），回退用前一拍的间隔
                    referenceInterval = closestIndex > 0
                            ? closestBeat.t() - combatLine.get(closestIndex - 1).t()
                            : 500;
                }
            }
            toleranceMs = (long) (referenceInterval * toleranceProp);
        }

        // 判定
        if (minDiff <= toleranceMs) {
            return new HitResult(true, definition.damageRate(), definition, closestBeat);
        } else {
            return HitResult.miss(definition, closestBeat);
        }
    }
}

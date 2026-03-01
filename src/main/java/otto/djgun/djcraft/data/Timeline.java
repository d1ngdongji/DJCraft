package otto.djgun.djcraft.data;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 节拍轨道/时间线
 * 包含战斗判定轨道和多个特效轨道
 */
public record Timeline(
        @SerializedName("combat_line") List<BeatEvent> combatLine,
        Map<String, List<BeatEvent>> effectLines) {
    /**
     * 规范化构造
     */
    public Timeline {
        if (combatLine == null) {
            combatLine = Collections.emptyList();
        }
        if (effectLines == null) {
            effectLines = Collections.emptyMap();
        }
    }

    /**
     * 创建空时间线
     */
    public static Timeline empty() {
        return new Timeline(Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * 获取战斗轨道的节拍数量
     */
    public int getCombatBeatCount() {
        return combatLine != null ? combatLine.size() : 0;
    }

    /**
     * 获取所有特效轨道的节拍总数
     */
    public int getTotalEffectBeatCount() {
        if (effectLines == null)
            return 0;
        return effectLines.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 获取指定特效轨道
     */
    public List<BeatEvent> getEffectLine(String name) {
        if (effectLines == null)
            return Collections.emptyList();
        return effectLines.getOrDefault(name, Collections.emptyList());
    }
}

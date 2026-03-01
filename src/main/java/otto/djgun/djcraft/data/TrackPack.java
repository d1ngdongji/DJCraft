package otto.djgun.djcraft.data;

import java.util.Collections;
import java.util.Map;

/**
 * 完整的曲目包
 * 包含ID、元信息、节拍定义和时间线
 */
public record TrackPack(
        String id,
        TrackMeta meta,
        TrackSettings settings,
        Map<String, BeatDefinition> definitions,
        Timeline timeline) {
    /**
     * 规范化构造
     */
    public TrackPack {
        if (settings == null) {
            settings = TrackSettings.createDefault();
        }
        if (definitions == null) {
            definitions = Collections.emptyMap();
        }
        if (timeline == null) {
            timeline = Timeline.empty();
        }
    }

    /**
     * 获取指定名称的节拍定义
     */
    public BeatDefinition getDefinition(String name) {
        return definitions != null ? definitions.get(name) : null;
    }

    /**
     * 判断是否有指定的节拍定义
     */
    public boolean hasDefinition(String name) {
        return definitions != null && definitions.containsKey(name);
    }

    /**
     * 获取曲目总时长（毫秒）
     */
    public int getTotalDurationMs() {
        return meta != null ? meta.totalDurationMs() : 0;
    }

    /**
     * 获取 BPM
     */
    public int getBpm() {
        return meta != null ? meta.bpm() : 120;
    }

    /**
     * 获取时间轴偏移（毫秒）
     * 正值表示时间轴整体向后偏移（需要更晚触发）
     */
    public int getOffsetMs() {
        return meta != null ? meta.offsetMs() : 0;
    }

    /**
     * 获取战斗轨道的节拍数量
     */
    public int getCombatBeatCount() {
        return timeline != null ? timeline.getCombatBeatCount() : 0;
    }

    /**
     * 打印曲目包信息摘要
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TrackPack: ").append(id).append(" ===\n");

        if (meta != null) {
            sb.append("Meta:\n");
            sb.append("  - Author: ").append(meta.author()).append("\n");
            sb.append("  - BPM: ").append(meta.bpm()).append("\n");
            sb.append("  - Difficulty: ").append(meta.difficulty()).append("\n");
            sb.append("  - Duration: ").append(meta.totalDurationMs()).append("ms\n");
            sb.append("  - Offset: ").append(meta.offsetMs()).append("ms\n");
        }

        sb.append("Definitions: ").append(definitions != null ? definitions.size() : 0).append(" types\n");
        if (definitions != null) {
            for (String defName : definitions.keySet()) {
                BeatDefinition def = definitions.get(defName);
                sb.append("  - ").append(defName)
                        .append(" (canAttack=").append(def.canAttack())
                        .append(", scale=").append(def.scale())
                        .append(", damageRate=").append(def.damageRate())
                        .append(")\n");
            }
        }

        sb.append("Timeline:\n");
        if (timeline != null) {
            sb.append("  - Combat beats: ").append(timeline.getCombatBeatCount()).append("\n");
            sb.append("  - Effect lines: ").append(timeline.effectLines().size()).append("\n");
            sb.append("  - Total effect beats: ").append(timeline.getTotalEffectBeatCount()).append("\n");

            // 打印前5个战斗节拍
            if (timeline.combatLine() != null && !timeline.combatLine().isEmpty()) {
                sb.append("  - First 5 combat beats:\n");
                int count = Math.min(5, timeline.combatLine().size());
                for (int i = 0; i < count; i++) {
                    BeatEvent beat = timeline.combatLine().get(i);
                    sb.append("      t=").append(beat.t()).append("ms, type=").append(beat.type());
                    if (beat.hasProps()) {
                        sb.append(", props=").append(beat.props());
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }
}

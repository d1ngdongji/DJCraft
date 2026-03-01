package otto.djgun.djcraft.data;

import java.util.Collections;
import java.util.Map;

/**
 * 时间线上的节拍事件
 * 表示在特定时间点发生的节拍
 */
public record BeatEvent(
        int t, // 时间（毫秒）
        String type, // 引用 definitions 中的类型名称
        Map<String, Object> props // 可选的覆盖属性
) {
    /**
     * 规范化构造，确保 props 不为 null
     */
    public BeatEvent {
        if (props == null) {
            props = Collections.emptyMap();
        }
    }

    /**
     * 简化构造器 - 不带覆盖属性
     */
    public BeatEvent(int t, String type) {
        this(t, type, Collections.emptyMap());
    }

    /**
     * 判断是否有覆盖属性
     */
    public boolean hasProps() {
        return props != null && !props.isEmpty();
    }

    /**
     * 获取指定的覆盖属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProp(String key, T defaultValue) {
        if (props == null || !props.containsKey(key)) {
            return defaultValue;
        }
        try {
            return (T) props.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}

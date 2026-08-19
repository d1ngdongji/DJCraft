package otto.djgun.djcraft.data;

import com.google.gson.annotations.SerializedName;
import java.util.Locale;
import java.util.Map;

/**
 * 节拍类型定义
 * 定义节拍的可视化属性、战斗类别和特效
 */
public record BeatDefinition(
        @SerializedName("can_attack") boolean canAttack,
        String color,
        float scale,
        BeatCategory category,
        @SerializedName("haptic_intensity") float hapticIntensity,
        float tolerance,
        String particle, // 可选粒子特效
        String trigger, // 可选触发条件
        String texture,
        @SerializedName("landing_x_percent") float landingXPercent,
        @SerializedName("spawn_advance_ms") int spawnAdvanceMs,
        @SerializedName("hit_behavior") BeatPostJudgmentBehavior hitBehavior,
        @SerializedName("miss_behavior") BeatPostJudgmentBehavior missBehavior,
        @SerializedName("rotation_rpm") float rotationRpm,
        @SerializedName("matched_hit_behavior") BeatPostJudgmentBehavior matchedHitBehavior
) {
    public static final String DEFAULT_TEXTURE = "djcraft:textures/gui/beats/blue_beat.png";
    public static final float DEFAULT_LANDING_X_PERCENT = 50.0f;
    public static final int DEFAULT_SPAWN_ADVANCE_MS = 1400;
    public static final BeatPostJudgmentBehavior DEFAULT_HIT_BEHAVIOR =
            BeatPostJudgmentBehavior.FREEZE_DISSIPATE;
    public static final BeatPostJudgmentBehavior DEFAULT_MISS_BEHAVIOR =
            BeatPostJudgmentBehavior.NONE;

    public BeatDefinition {
        if (category == null) {
            category = BeatCategory.NORMAL;
        }
        if (texture == null || texture.isBlank()) {
            texture = DEFAULT_TEXTURE;
        }
        if (!Float.isFinite(landingXPercent) || landingXPercent < 0.0f || landingXPercent > 100.0f) {
            landingXPercent = DEFAULT_LANDING_X_PERCENT;
        }
        if (spawnAdvanceMs <= 0 || spawnAdvanceMs > 60_000) {
            spawnAdvanceMs = DEFAULT_SPAWN_ADVANCE_MS;
        }
        if (hitBehavior == null) {
            hitBehavior = DEFAULT_HIT_BEHAVIOR;
        }
        if (missBehavior == null) {
            missBehavior = DEFAULT_MISS_BEHAVIOR;
        }
        if (matchedHitBehavior == null) {
            matchedHitBehavior = hitBehavior;
        }
        if (!Float.isFinite(rotationRpm) || rotationRpm < -10_000.0f || rotationRpm > 10_000.0f) {
            rotationRpm = 0.0f;
        }
    }

    /** Constructor retaining the first Falling HUD schema shape. */
    public BeatDefinition(boolean canAttack, String color, float scale, BeatCategory category,
            float hapticIntensity, float tolerance, String particle, String trigger,
            String texture, float landingXPercent, int spawnAdvanceMs,
            BeatPostJudgmentBehavior hitBehavior, BeatPostJudgmentBehavior missBehavior,
            float rotationRpm) {
        this(canAttack, color, scale, category, hapticIntensity, tolerance, particle, trigger,
                texture, landingXPercent, spawnAdvanceMs, hitBehavior, missBehavior, rotationRpm, null);
    }

    /** Compatible constructor for callers compiled against the pre-falling-HUD shape. */
    public BeatDefinition(boolean canAttack, String color, float scale, BeatCategory category,
            float hapticIntensity, float tolerance, String particle, String trigger) {
        this(canAttack, color, scale, category, hapticIntensity, tolerance, particle, trigger,
                DEFAULT_TEXTURE, DEFAULT_LANDING_X_PERCENT, DEFAULT_SPAWN_ADVANCE_MS,
                DEFAULT_HIT_BEHAVIOR, DEFAULT_MISS_BEHAVIOR, 0.0f);
    }

    /**
     * 创建默认的普通节拍定义
     */
    public static BeatDefinition createDefault() {
        return new BeatDefinition(true, "#FFFFFF", 1.0f, BeatCategory.NORMAL, 1.0f, 0.1f, null, null,
                DEFAULT_TEXTURE, DEFAULT_LANDING_X_PERCENT, DEFAULT_SPAWN_ADVANCE_MS,
                DEFAULT_HIT_BEHAVIOR, DEFAULT_MISS_BEHAVIOR, 0.0f);
    }

    /**
     * 判断是否有粒子特效
     */
    public boolean hasParticle() {
        return particle != null && !particle.isEmpty();
    }

    /**
     * 判断是否有触发条件
     */
    public boolean hasTrigger() {
        return trigger != null && !trigger.isEmpty();
    }

    public BeatPostJudgmentBehavior behaviorAfterJudgment(
            boolean hit, boolean categoryMatched) {
        if (!hit) {
            return missBehavior;
        }
        return categoryMatched ? matchedHitBehavior : hitBehavior;
    }

    /**
     * Applies the supported per-event {@link BeatEvent#props()} overrides.
     * Unknown keys and values of the wrong type are ignored.
     */
    public BeatDefinition withOverrides(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return this;
        }

        return new BeatDefinition(
                booleanProp(props, "can_attack", canAttack),
                stringProp(props, "color", color),
                floatProp(props, "scale", scale),
                categoryProp(props, category),
                floatProp(props, "haptic_intensity", hapticIntensity),
                floatProp(props, "tolerance", tolerance),
                stringProp(props, "particle", particle),
                stringProp(props, "trigger", trigger),
                stringProp(props, "texture", texture),
                rangedFloatProp(props, "landing_x_percent", landingXPercent, 0.0f, 100.0f),
                rangedIntProp(props, "spawn_advance_ms", spawnAdvanceMs, 1, 60_000),
                behaviorProp(props, "hit_behavior", hitBehavior),
                behaviorProp(props, "miss_behavior", missBehavior),
                rangedFloatProp(props, "rotation_rpm", rotationRpm, -10_000.0f, 10_000.0f),
                behaviorProp(props, "matched_hit_behavior", matchedHitBehavior));
    }

    private static boolean booleanProp(Map<String, Object> props, String key, boolean fallback) {
        Object value = props.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static String stringProp(Map<String, Object> props, String key, String fallback) {
        Object value = props.get(key);
        return value instanceof String string ? string : fallback;
    }

    private static float floatProp(Map<String, Object> props, String key, float fallback) {
        Object value = props.get(key);
        if (!(value instanceof Number number)) {
            return fallback;
        }
        float result = number.floatValue();
        return Float.isFinite(result) ? result : fallback;
    }

    private static float rangedFloatProp(Map<String, Object> props, String key, float fallback,
            float minimum, float maximum) {
        float result = floatProp(props, key, fallback);
        return result >= minimum && result <= maximum ? result : fallback;
    }

    private static int rangedIntProp(Map<String, Object> props, String key, int fallback,
            int minimum, int maximum) {
        Object value = props.get(key);
        if (!(value instanceof Number number)) {
            return fallback;
        }
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < minimum || raw > maximum) {
            return fallback;
        }
        return (int) raw;
    }

    private static BeatPostJudgmentBehavior behaviorProp(Map<String, Object> props, String key,
            BeatPostJudgmentBehavior fallback) {
        Object value = props.get(key);
        return value instanceof String id ? BeatPostJudgmentBehavior.fromId(id, fallback) : fallback;
    }

    private static BeatCategory categoryProp(Map<String, Object> props, BeatCategory fallback) {
        Object value = props.get("category");
        if (!(value instanceof String id)) {
            return fallback;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "normal" -> BeatCategory.NORMAL;
            case "weakbeat" -> BeatCategory.WEAKBEAT;
            case "downbeat" -> BeatCategory.DOWNBEAT;
            default -> fallback;
        };
    }
}

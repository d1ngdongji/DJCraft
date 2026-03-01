package otto.djgun.djcraft.data;

import com.google.gson.annotations.SerializedName;

/**
 * 节拍类型定义
 * 定义节拍的可视化属性、伤害参数和特效
 */
public record BeatDefinition(
        @SerializedName("can_attack") boolean canAttack,
        String color,
        float scale,
        @SerializedName("damage_rate") float damageRate,
        @SerializedName("haptic_intensity") float hapticIntensity,
        float tolerance,
        String particle, // 可选粒子特效
        String trigger // 可选触发条件
) {
    /**
     * 创建默认的普通节拍定义
     */
    public static BeatDefinition createDefault() {
        return new BeatDefinition(true, "#FFFFFF", 1.0f, 1.0f, 1.0f, 0.1f, null, null);
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
}

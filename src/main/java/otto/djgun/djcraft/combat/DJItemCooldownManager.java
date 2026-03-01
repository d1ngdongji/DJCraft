package otto.djgun.djcraft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理物品的 DJ 冷却节拍数和切换前摇
 */
public class DJItemCooldownManager {

    /** 物品 DJ 冷却覆盖 (beats) */
    private static final Map<Item, Integer> CONFIG_OVERRIDES = new HashMap<>();

    /** 物品切换前摇覆盖 (beats)；未指定时默认使用该物品的 DJ 冷却值 */
    private static final Map<Item, Integer> SWITCH_WARMUP_OVERRIDES = new HashMap<>();

    /**
     * 重载配置
     * 应该在 ModConfigEvent 中调用
     */
    public static void reloadConfig() {
        CONFIG_OVERRIDES.clear();
        SWITCH_WARMUP_OVERRIDES.clear();

        // 加载 DJ 冷却覆盖
        loadOverrides("itemBeatCooldowns", Config.ITEM_BEAT_COOLDOWNS.get(), CONFIG_OVERRIDES);

        // 加载切换前摇覆盖
        loadOverrides("itemSwitchWarmups", Config.ITEM_SWITCH_WARMUPS.get(), SWITCH_WARMUP_OVERRIDES);

        DJCraft.LOGGER.info("Loaded {} beat cooldown overrides, {} switch warmup overrides",
                CONFIG_OVERRIDES.size(), SWITCH_WARMUP_OVERRIDES.size());
    }

    private static void loadOverrides(String configName, List<? extends String> entries, Map<Item, Integer> target) {
        try {
            for (String entry : entries) {
                try {
                    String[] parts = entry.split("=");
                    if (parts.length == 2) {
                        ResourceLocation itemId = ResourceLocation.parse(parts[0].trim());
                        int beats = Integer.parseInt(parts[1].trim());
                        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                        if (itemOpt.isPresent()) {
                            target.put(itemOpt.get(), beats);
                        } else {
                            DJCraft.LOGGER.warn("Unknown item in {} config: {}", configName, itemId);
                        }
                    }
                } catch (Exception e) {
                    DJCraft.LOGGER.error("Error parsing {} config entry: {}", configName, entry, e);
                }
            }
        } catch (Exception e) {
            DJCraft.LOGGER.error("Failed to load {} config", configName, e);
        }
    }

    /**
     * 获取物品的 DJ 冷却节拍数
     */
    public static int getBeatCooldown(ItemStack stack) {
        if (stack.isEmpty())
            return 1;

        Item item = stack.getItem();

        // 1. 检查配置覆盖
        if (CONFIG_OVERRIDES.containsKey(item)) {
            return CONFIG_OVERRIDES.get(item);
        }

        // 2. 根据攻击速度计算
        return calcBeatsFromAttackSpeed(stack);
    }

    /**
     * 获取切换到此物品时的前摇节拍数
     * 默认值 = 该物品的 DJ 冷却节拍数
     */
    public static int getSwitchWarmup(ItemStack stack) {
        if (stack.isEmpty())
            return 0;

        Item item = stack.getItem();

        // 1. 检查前摇配置覆盖
        if (SWITCH_WARMUP_OVERRIDES.containsKey(item)) {
            return SWITCH_WARMUP_OVERRIDES.get(item);
        }

        // 2. 默认等于该物品的 DJ 冷却值
        return getBeatCooldown(stack);
    }

    /**
     * 根据物品攻击速度属性计算节拍数
     */
    private static int calcBeatsFromAttackSpeed(ItemStack stack) {
        // 默认玩家基础攻速是 4.0
        double baseAttackSpeed = 4.0;

        // 获取物品的 AttributeModifiers
        var acceptedModifiers = stack.getAttributeModifiers();
        final double[] speedModifier = { 0.0 };

        // 遍历主手的属性修饰符
        acceptedModifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_SPEED)) {
                speedModifier[0] += modifier.amount();
            }
        });

        double finalSpeed = baseAttackSpeed + speedModifier[0];

        // 规则映射
        // [1.5, inf) -> 1 beat
        // [1.0, 1.5) -> 2 beats
        // [0.6, 1.0) -> 3 beats
        // (0, 0.6) -> 4 beats
        if (finalSpeed >= 1.5)
            return 1;
        if (finalSpeed >= 1.0)
            return 2;
        if (finalSpeed >= 0.6)
            return 3;
        return 4;
    }
}

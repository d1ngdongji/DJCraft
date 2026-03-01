package otto.djgun.djcraft;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_HUD = BUILDER
                        .comment("Enable DJ Debug HUD")
                        .define("enableDebugHud", false);

        public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_SOUND = BUILDER
                        .comment("Enable Debug Sound (anvil landing on each beat)")
                        .define("enableDebugSound", false);

        public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BEAT_COOLDOWNS = BUILDER
                        .comment("Custom beat cooldowns for items. Format: 'modid:item_id=beats' (e.g. 'minecraft:diamond_sword=2')")
                        .defineListAllowEmpty("itemBeatCooldowns", List.of(), () -> "",
                                        Config::validateItemBeatCooldown);

        public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_SWITCH_WARMUPS = BUILDER
                        .comment("Custom switch warmup for items (beats to wait when switching TO this item)."
                                        + " Format: 'modid:item_id=beats'. Defaults to the item's DJ beat cooldown if not set.")
                        .defineListAllowEmpty("itemSwitchWarmups", List.of(), () -> "",
                                        Config::validateItemBeatCooldown); // 复用相同的格式验证

        static final ModConfigSpec SPEC = BUILDER.build();

        private static boolean validateItemBeatCooldown(final Object obj) {
                if (!(obj instanceof String str))
                        return false;
                String[] parts = str.split("=");
                if (parts.length != 2)
                        return false;
                try {
                        ResourceLocation.parse(parts[0]);
                        Integer.parseInt(parts[1]);
                        return true;
                } catch (Exception e) {
                        return false;
                }
        }
}

package otto.djgun.djcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
        public static final int DEFAULT_MAX_TRACKPACK_DOWNLOAD_MIB = 256;
        public static final int DEFAULT_TOLERANCE_RECHARGE_TICKS = 80;

        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_HUD = BUILDER
                        .comment("Enable DJ Debug HUD")
                        .define("enableDebugHud", false);

        public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_SOUND = BUILDER
                        .comment("Enable Debug Sound (anvil landing on each beat)")
                        .define("enableDebugSound", false);

        public static final ModConfigSpec.IntValue MAX_TRACKPACK_DOWNLOAD_MIB = BUILDER
                        .comment("Maximum compressed and uncompressed TrackPack download size in MiB")
                        .defineInRange("maxTrackPackDownloadMiB", DEFAULT_MAX_TRACKPACK_DOWNLOAD_MIB, 1, 2048);

        public static final ModConfigSpec.IntValue MAX_AIR_JUMPS = BUILDER
                        .comment("Maximum successful DJ air jumps before the player lands")
                        .defineInRange("maxAirJumps", 1, 0, 16);

        public static final ModConfigSpec.IntValue TOLERANCE_RECHARGE_TICKS = BUILDER
                        .comment("Active DJ session ticks required to restore one combo-protection chance")
                        .defineInRange("toleranceRechargeTicks", DEFAULT_TOLERANCE_RECHARGE_TICKS, 1,
                                        Integer.MAX_VALUE);

        public static final ModConfigSpec.DoubleValue DASH_HORIZONTAL_SPEED = BUILDER
                        .comment("Directional horizontal impulse added by the DJ dash")
                        .defineInRange("dashHorizontalSpeed", 1.5, 0.0, 16.0);

        static final ModConfigSpec SPEC = BUILDER.build();

        public static long maxTrackPackBytes() {
                try {
                        return MAX_TRACKPACK_DOWNLOAD_MIB.get() * 1024L * 1024L;
                } catch (IllegalStateException ignored) {
                        return DEFAULT_MAX_TRACKPACK_DOWNLOAD_MIB * 1024L * 1024L;
                }
        }

        public static int toleranceRechargeTicks() {
                try {
                        return TOLERANCE_RECHARGE_TICKS.get();
                } catch (IllegalStateException ignored) {
                        return DEFAULT_TOLERANCE_RECHARGE_TICKS;
                }
        }
}

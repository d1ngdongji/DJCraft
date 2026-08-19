package otto.djgun.djcraft.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DJClientConfig {
    public static final int DEFAULT_FALLING_JUDGE_LINE_Y_PERCENT = 66;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<BeatPresentationMode> BEAT_PRESENTATION_MODE = BUILDER
            .comment("Beat chart presentation mode")
            .defineEnum("beatPresentationMode", BeatPresentationMode.FALLING);

    public static final ModConfigSpec.IntValue FALLING_JUDGE_LINE_Y_PERCENT = BUILDER
            .comment("Vertical position of the falling-mode judgment line as a GUI height percentage")
            .defineInRange("fallingJudgeLineYPercent", DEFAULT_FALLING_JUDGE_LINE_Y_PERCENT, 25, 85);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private DJClientConfig() {
    }

    public static BeatPresentationMode beatPresentationMode() {
        try {
            return BEAT_PRESENTATION_MODE.get();
        } catch (IllegalStateException ignored) {
            return BeatPresentationMode.FALLING;
        }
    }

    public static int fallingJudgeLineYPercent() {
        try {
            return FALLING_JUDGE_LINE_Y_PERCENT.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_FALLING_JUDGE_LINE_Y_PERCENT;
        }
    }
}

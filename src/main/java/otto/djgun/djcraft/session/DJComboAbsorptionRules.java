package otto.djgun.djcraft.session;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Server-side absorption reward for earned combo milestones. */
public final class DJComboAbsorptionRules {
    public static final int COMBO_INTERVAL = 10;
    public static final int DURATION_TICKS = 30 * 20;
    public static final int AMPLIFIER = 1;

    private DJComboAbsorptionRules() {
    }

    public static boolean shouldReward(int combo) {
        return combo > 0 && combo % COMBO_INTERVAL == 0;
    }

    public static void reward(Player player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, DURATION_TICKS, AMPLIFIER));
    }
}

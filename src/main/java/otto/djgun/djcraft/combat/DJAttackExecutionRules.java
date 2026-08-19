package otto.djgun.djcraft.combat;

import net.minecraft.world.item.ItemStack;

/** Separates beat success from permission to execute a weakened off-beat attack. */
public final class DJAttackExecutionRules {
    private DJAttackExecutionRules() {
    }

    public static boolean canExecute(boolean validAction, HitResult judgment, int offBeatDamagePercent) {
        return validAction && (judgment.isHit() || Math.clamp(offBeatDamagePercent, 0, 100) > 0);
    }

    public static float damageMultiplier(HitResult judgment, ItemStack stack, int offBeatDamagePercent) {
        if (judgment.isHit()) {
            return DJBeatDamageRules.multiplier(judgment.beatData(), stack);
        }
        return offBeatDamageMultiplier(offBeatDamagePercent);
    }

    static float offBeatDamageMultiplier(int offBeatDamagePercent) {
        return Math.clamp(offBeatDamagePercent, 0, 100) / 100.0F;
    }
}

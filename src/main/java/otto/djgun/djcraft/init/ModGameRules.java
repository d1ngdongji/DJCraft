package otto.djgun.djcraft.init;

import net.minecraft.world.level.GameRules;

/** World-persistent server authority for DJ combat tuning. */
public final class ModGameRules {
    public static final int DEFAULT_OFF_BEAT_ATTACK_DAMAGE_PERCENT = 50;
    public static final int DEFAULT_BASE_MAX_TOLERANCE_CHANCES = 2;
    public static final int DEFAULT_IDLE_ATTACKABLE_BEATS_BEFORE_COMBO_RESET = 5;

    public static final GameRules.Key<GameRules.IntegerValue> OFF_BEAT_ATTACK_DAMAGE_PERCENT =
            GameRules.register("djcraftOffBeatAttackDamagePercent", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(DEFAULT_OFF_BEAT_ATTACK_DAMAGE_PERCENT));
    public static final GameRules.Key<GameRules.IntegerValue> BASE_MAX_TOLERANCE_CHANCES =
            GameRules.register("djcraftBaseMaxToleranceChances", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(DEFAULT_BASE_MAX_TOLERANCE_CHANCES));
    public static final GameRules.Key<GameRules.IntegerValue> IDLE_ATTACKABLE_BEATS_BEFORE_COMBO_RESET =
            GameRules.register("djcraftIdleAttackableBeatsBeforeComboReset", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(DEFAULT_IDLE_ATTACKABLE_BEATS_BEFORE_COMBO_RESET));

    private ModGameRules() {
    }

    /** Forces static registration before a server constructs its world rules. */
    public static void bootstrap() {
    }

    public static int offBeatAttackDamagePercent(GameRules rules) {
        return sanitizeOffBeatAttackDamagePercent(rules.getInt(OFF_BEAT_ATTACK_DAMAGE_PERCENT));
    }

    public static int baseMaxToleranceChances(GameRules rules) {
        return sanitizeBaseMaxToleranceChances(rules.getInt(BASE_MAX_TOLERANCE_CHANCES));
    }

    public static int maxToleranceChances(GameRules rules, int bonus) {
        return saturatedAdd(baseMaxToleranceChances(rules), Math.max(0, bonus));
    }

    public static int idleAttackableBeatsBeforeComboReset(GameRules rules) {
        return sanitizeIdleAttackableBeatsBeforeComboReset(
                rules.getInt(IDLE_ATTACKABLE_BEATS_BEFORE_COMBO_RESET));
    }

    public static int idleAttackableBeatsBeforeComboReset(GameRules rules, int bonus) {
        return saturatedAdd(idleAttackableBeatsBeforeComboReset(rules), Math.max(0, bonus));
    }

    static int sanitizeOffBeatAttackDamagePercent(int value) {
        return Math.clamp(value, 0, 100);
    }

    static int sanitizeBaseMaxToleranceChances(int value) {
        return Math.clamp(value, 0, 16);
    }

    static int sanitizeIdleAttackableBeatsBeforeComboReset(int value) {
        return Math.max(1, value);
    }

    private static int saturatedAdd(int value, int bonus) {
        return value > Integer.MAX_VALUE - bonus ? Integer.MAX_VALUE : value + bonus;
    }
}

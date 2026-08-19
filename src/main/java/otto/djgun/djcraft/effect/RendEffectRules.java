package otto.djgun.djcraft.effect;

public final class RendEffectRules {
    public static final float DAMAGE_PER_LEVEL = 2.0F;

    private RendEffectRules() {
    }

    public static float bonusDamage(int amplifier) {
        return DAMAGE_PER_LEVEL * (Math.max(0, amplifier) + 1);
    }

    public static boolean shouldAmplify(boolean clientSide, boolean hasAttacker, float damage) {
        return !clientSide && hasAttacker && damage > 0.0F;
    }
}

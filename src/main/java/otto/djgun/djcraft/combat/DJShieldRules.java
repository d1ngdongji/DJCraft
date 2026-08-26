package otto.djgun.djcraft.combat;

public final class DJShieldRules {
    public static final double START_ENERGY_COST = 5.0;
    public static final double SUSTAIN_ENERGY_COST = 5.0;
    public static final double PARRY_ENERGY_REWARD = 5.0;
    public static final float PARRY_DAMAGE_MULTIPLIER = 0.6F;
    public static final double PARRY_DURATION_BEATS = 1.0;
    public static final double SUSTAIN_INTERVAL_BEATS = 1.0;
    public static final double PARRY_HORIZONTAL_DISTANCE_BLOCKS = 5.0;
    public static final double PARRY_VERTICAL_HEIGHT_BLOCKS = 3.0;
    // Calibrated for standard LivingEntity gravity/drag to travel roughly the distances above.
    public static final double PARRY_HORIZONTAL_VELOCITY = 0.55;
    public static final double PARRY_VERTICAL_VELOCITY = 0.68;
    public static final long START_AUTHORIZATION_TTL_TICKS = 4L;

    private DJShieldRules() {
    }
}

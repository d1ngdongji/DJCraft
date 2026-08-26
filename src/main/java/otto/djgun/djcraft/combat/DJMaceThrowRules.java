package otto.djgun.djcraft.combat;

/** Fixed gameplay and presentation tuning for the DJ mace throw. */
public final class DJMaceThrowRules {
    public static final float BASE_DAMAGE = 16.0F;
    public static final float INITIAL_SPEED = 1.75F;
    public static final float COLLISION_DIAMETER = 0.75F;
    public static final double GRAVITY = 0.07;
    public static final int MAX_LIFETIME_TICKS = 60;
    public static final double VERTICAL_KNOCKBACK = 1.6;
    public static final float ROTATION_DEGREES_PER_TICK = 36.0F;

    private DJMaceThrowRules() {
    }
}

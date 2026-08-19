package otto.djgun.djcraft.api.combat;

/** Truncated forward-cone targeting used by ordinary DJ melee attacks. */
public record DJSoftTargetMeleeBehavior(
        double reach,
        double horizontalAngleDegrees,
        double verticalAngleDegrees) implements DJMeleeBehavior {
    public static final double DEFAULT_REACH = 4.25;
    public static final double DEFAULT_HORIZONTAL_ANGLE_DEGREES = 30.0;
    public static final double DEFAULT_VERTICAL_ANGLE_DEGREES = 20.0;
    public static final double MAX_DISTANCE = 64.0;

    public DJSoftTargetMeleeBehavior {
        requireDistance(reach, "reach");
        requireAngle(horizontalAngleDegrees, "horizontal_angle_degrees");
        requireAngle(verticalAngleDegrees, "vertical_angle_degrees");
    }

    public static DJSoftTargetMeleeBehavior defaults() {
        return new DJSoftTargetMeleeBehavior(DEFAULT_REACH,
                DEFAULT_HORIZONTAL_ANGLE_DEGREES, DEFAULT_VERTICAL_ANGLE_DEGREES);
    }

    @Override
    public boolean area() {
        return false;
    }

    static void requireDistance(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > MAX_DISTANCE) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 64]");
        }
    }

    static void requireAngle(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value >= 90.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 90)");
        }
    }
}

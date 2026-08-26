package otto.djgun.djcraft.combat;

/** Pure continuous AABB collision math for two linearly moving bodies. */
public final class DJProjectileCollisionMath {
    private static final double EPSILON = 1.0E-9;

    private DJProjectileCollisionMath() {
    }

    /**
     * Returns the first contact fraction in {@code [0, 1]}, or positive infinity when the
     * two boxes do not overlap during the step.
     */
    public static double firstContactFraction(Box projectileStart, Vector projectileMovement,
            Box targetStart, Vector targetMovement) {
        Vector relativeMovement = projectileMovement.subtract(targetMovement);
        Interval interval = new Interval(0.0, 1.0);
        if (!clipAxis(projectileStart.minX, projectileStart.maxX,
                targetStart.minX, targetStart.maxX, relativeMovement.x, interval)
                || !clipAxis(projectileStart.minY, projectileStart.maxY,
                        targetStart.minY, targetStart.maxY, relativeMovement.y, interval)
                || !clipAxis(projectileStart.minZ, projectileStart.maxZ,
                        targetStart.minZ, targetStart.maxZ, relativeMovement.z, interval)) {
            return Double.POSITIVE_INFINITY;
        }
        return interval.entry <= interval.exit + EPSILON ? Math.max(0.0, interval.entry)
                : Double.POSITIVE_INFINITY;
    }

    private static boolean clipAxis(double projectileMin, double projectileMax,
            double targetMin, double targetMax, double velocity, Interval interval) {
        if (Math.abs(velocity) <= EPSILON) {
            return projectileMax >= targetMin - EPSILON && projectileMin <= targetMax + EPSILON;
        }

        double first = (targetMin - projectileMax) / velocity;
        double second = (targetMax - projectileMin) / velocity;
        interval.entry = Math.max(interval.entry, Math.min(first, second));
        interval.exit = Math.min(interval.exit, Math.max(first, second));
        return interval.entry <= interval.exit + EPSILON;
    }

    public record Vector(double x, double y, double z) {
        public Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }
    }

    public record Box(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
    }

    private static final class Interval {
        private double entry;
        private double exit;

        private Interval(double entry, double exit) {
            this.entry = entry;
            this.exit = exit;
        }
    }
}

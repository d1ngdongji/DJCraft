package otto.djgun.djcraft.api.combat;

/** Registered one-ended capsule strategy: flat near end, rounded far end. */
public non-sealed class DJAreaMeleeBehavior implements DJMeleeBehavior {
    private final double cylinderLength;
    private final double radius;
    private final boolean primaryItemEffectsOnly;
    private final boolean resetFallDistanceAfterContact;

    public DJAreaMeleeBehavior(double cylinderLength, double radius) {
        this(cylinderLength, radius, false, false);
    }

    public DJAreaMeleeBehavior(double cylinderLength, double radius,
            boolean primaryItemEffectsOnly, boolean resetFallDistanceAfterContact) {
        DJSoftTargetMeleeBehavior.requireDistance(cylinderLength, "cylinder_length");
        DJSoftTargetMeleeBehavior.requireDistance(radius, "radius");
        this.cylinderLength = cylinderLength;
        this.radius = radius;
        this.primaryItemEffectsOnly = primaryItemEffectsOnly;
        this.resetFallDistanceAfterContact = resetFallDistanceAfterContact;
    }

    @Override
    public final boolean area() {
        return true;
    }

    public final double cylinderLength() {
        return cylinderLength;
    }

    public final double radius() {
        return radius;
    }

    public final boolean primaryItemEffectsOnly() {
        return primaryItemEffectsOnly;
    }

    public final boolean resetFallDistanceAfterContact() {
        return resetFallDistanceAfterContact;
    }

    public DJAreaMeleeBehavior withDimensions(double newCylinderLength, double newRadius) {
        return new DJAreaMeleeBehavior(newCylinderLength, newRadius,
                primaryItemEffectsOnly, resetFallDistanceAfterContact);
    }
}

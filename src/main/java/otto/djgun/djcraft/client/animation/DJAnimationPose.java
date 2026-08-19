package otto.djgun.djcraft.client.animation;

/**
 * Immutable first-person pose split between hand space and item-center space.
 * The seven-value constructor remains hand-space shorthand for public API compatibility.
 */
public record DJAnimationPose(
        DJAnimationTransform handSpace,
        DJAnimationTransform itemCenterSpace) {

    public static final DJAnimationPose IDENTITY =
            new DJAnimationPose(DJAnimationTransform.IDENTITY, DJAnimationTransform.IDENTITY);

    public DJAnimationPose {
        if (handSpace == null || itemCenterSpace == null) {
            throw new IllegalArgumentException("Animation pose spaces must not be null");
        }
    }

    public DJAnimationPose(float translationXBlocks, float translationYBlocks, float translationZBlocks,
            float rotationXDegrees, float rotationYDegrees, float rotationZDegrees, float scaleRatio) {
        this(new DJAnimationTransform(
                        translationXBlocks, translationYBlocks, translationZBlocks,
                        rotationXDegrees, rotationYDegrees, rotationZDegrees, scaleRatio),
                DJAnimationTransform.IDENTITY);
    }

    public static DJAnimationPose itemCenter(DJAnimationTransform transform) {
        return new DJAnimationPose(DJAnimationTransform.IDENTITY, transform);
    }

    public DJAnimationPose mirrored(float handSign) {
        return new DJAnimationPose(
                handSpace.mirrored(handSign), itemCenterSpace.mirrored(handSign));
    }

    public DJAnimationPose plus(DJAnimationPose other) {
        return new DJAnimationPose(
                handSpace.plus(other.handSpace), itemCenterSpace.plus(other.itemCenterSpace));
    }

    public DJAnimationPose interpolate(DJAnimationPose other, float phase01) {
        return new DJAnimationPose(
                handSpace.interpolate(other.handSpace, phase01),
                itemCenterSpace.interpolate(other.itemCenterSpace, phase01));
    }

    // Compatibility accessors expose the migrated hand-space track.
    public float translationXBlocks() {
        return handSpace.translationXBlocks();
    }

    public float translationYBlocks() {
        return handSpace.translationYBlocks();
    }

    public float translationZBlocks() {
        return handSpace.translationZBlocks();
    }

    public float rotationXDegrees() {
        return handSpace.rotationXDegrees();
    }

    public float rotationYDegrees() {
        return handSpace.rotationYDegrees();
    }

    public float rotationZDegrees() {
        return handSpace.rotationZDegrees();
    }

    public float scaleRatio() {
        return handSpace.scaleRatio();
    }
}

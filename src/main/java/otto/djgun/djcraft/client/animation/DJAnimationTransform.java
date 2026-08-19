package otto.djgun.djcraft.client.animation;

/** One immutable transform inside a declared first-person coordinate space. */
public record DJAnimationTransform(
        float translationXBlocks,
        float translationYBlocks,
        float translationZBlocks,
        float rotationXDegrees,
        float rotationYDegrees,
        float rotationZDegrees,
        float scaleRatio) {

    public static final DJAnimationTransform IDENTITY =
            new DJAnimationTransform(0, 0, 0, 0, 0, 0, 1);

    public DJAnimationTransform {
        if (!Float.isFinite(translationXBlocks) || !Float.isFinite(translationYBlocks)
                || !Float.isFinite(translationZBlocks) || !Float.isFinite(rotationXDegrees)
                || !Float.isFinite(rotationYDegrees) || !Float.isFinite(rotationZDegrees)
                || !Float.isFinite(scaleRatio) || scaleRatio <= 0.0f) {
            throw new IllegalArgumentException("Animation transform contains an invalid value");
        }
    }

    public DJAnimationTransform mirrored(float handSign) {
        return new DJAnimationTransform(
                translationXBlocks * handSign, translationYBlocks, translationZBlocks,
                rotationXDegrees, rotationYDegrees * handSign, rotationZDegrees * handSign,
                scaleRatio);
    }

    public DJAnimationTransform plus(DJAnimationTransform other) {
        return new DJAnimationTransform(
                translationXBlocks + other.translationXBlocks,
                translationYBlocks + other.translationYBlocks,
                translationZBlocks + other.translationZBlocks,
                rotationXDegrees + other.rotationXDegrees,
                rotationYDegrees + other.rotationYDegrees,
                rotationZDegrees + other.rotationZDegrees,
                scaleRatio * other.scaleRatio);
    }

    public DJAnimationTransform interpolate(DJAnimationTransform other, float phase01) {
        return new DJAnimationTransform(
                lerp(translationXBlocks, other.translationXBlocks, phase01),
                lerp(translationYBlocks, other.translationYBlocks, phase01),
                lerp(translationZBlocks, other.translationZBlocks, phase01),
                lerp(rotationXDegrees, other.rotationXDegrees, phase01),
                lerp(rotationYDegrees, other.rotationYDegrees, phase01),
                lerp(rotationZDegrees, other.rotationZDegrees, phase01),
                lerp(scaleRatio, other.scaleRatio, phase01));
    }

    private static float lerp(float from, float to, float phase01) {
        return from + (to - from) * phase01;
    }
}

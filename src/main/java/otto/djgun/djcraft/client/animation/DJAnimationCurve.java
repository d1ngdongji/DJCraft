package otto.djgun.djcraft.client.animation;

import java.util.Arrays;

/** Immutable, allocation-light piecewise-linear pose curve in normalized phase space. */
public final class DJAnimationCurve {
    public record Keyframe(float phase01, DJAnimationPose pose) {
        public Keyframe {
            if (!Float.isFinite(phase01) || phase01 < 0.0f || phase01 > 1.0f || pose == null) {
                throw new IllegalArgumentException("Invalid animation keyframe");
            }
        }
    }

    private final Keyframe[] keyframes;

    public DJAnimationCurve(Keyframe... keyframes) {
        if (keyframes == null || keyframes.length < 2) {
            throw new IllegalArgumentException("Animation curve requires at least two keyframes");
        }
        this.keyframes = keyframes.clone();
        float previous = -1.0f;
        for (Keyframe keyframe : this.keyframes) {
            if (keyframe == null || keyframe.phase01() <= previous) {
                throw new IllegalArgumentException("Animation keyframe phases must be strictly increasing");
            }
            previous = keyframe.phase01();
        }
        if (this.keyframes[0].phase01() != 0.0f
                || this.keyframes[this.keyframes.length - 1].phase01() != 1.0f) {
            throw new IllegalArgumentException("Animation curve must cover phase 0 through 1");
        }
    }

    public DJAnimationPose sample(float phase01) {
        float phase = Math.clamp(phase01, 0.0f, 1.0f);
        if (phase == 0.0f) {
            return keyframes[0].pose();
        }
        if (phase == 1.0f) {
            return keyframes[keyframes.length - 1].pose();
        }

        int low = 0;
        int high = keyframes.length - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (keyframes[middle].phase01() <= phase) {
                low = middle;
            } else {
                high = middle;
            }
        }

        Keyframe from = keyframes[low];
        Keyframe to = keyframes[high];
        float localPhase = (phase - from.phase01()) / (to.phase01() - from.phase01());
        return from.pose().interpolate(to.pose(), localPhase);
    }

    public Keyframe[] keyframes() {
        return Arrays.copyOf(keyframes, keyframes.length);
    }

}

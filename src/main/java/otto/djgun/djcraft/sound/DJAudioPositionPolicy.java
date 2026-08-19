package otto.djgun.djcraft.sound;

/** Validates raw OpenAL positions before they become the session clock. */
public final class DJAudioPositionPolicy {
    private static final long BACKWARD_JUMP_TOLERANCE_MS = 100L;

    private DJAudioPositionPolicy() {
    }

    public static boolean isDiscontinuousBackwardJump(long lastPositionMs, long candidatePositionMs) {
        return lastPositionMs > 0L
                && candidatePositionMs < lastPositionMs - BACKWARD_JUMP_TOLERANCE_MS;
    }
}

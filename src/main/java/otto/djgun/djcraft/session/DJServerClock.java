package otto.djgun.djcraft.session;

/**
 * Server-side DJ timeline clock. Client playback continues to use OpenAL.
 */
public interface DJServerClock {
    long currentTimeMs();

    default void setPaused(boolean paused) {
    }

    default boolean isPaused() {
        return false;
    }

    default void alignBackward(long correctionMs) {
    }
}

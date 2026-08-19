package otto.djgun.djcraft.sound;

/** Identity-only policy used before touching an OpenAL channel. */
public final class DJAudioBindingPolicy {
    private DJAudioBindingPolicy() {
    }

    public static boolean shouldBind(Object expected, Object candidate, boolean waiting, boolean stopped) {
        return waiting && !stopped && expected != null && expected == candidate;
    }

    public static boolean shouldClearBinding(Object boundChannel, Object stoppedChannel,
            int boundSourceId, int stoppedSourceId) {
        return boundChannel != null && boundChannel == stoppedChannel
                && boundSourceId == stoppedSourceId;
    }
}

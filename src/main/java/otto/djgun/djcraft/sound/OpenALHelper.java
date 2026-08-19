package otto.djgun.djcraft.sound;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import otto.djgun.djcraft.DJCraft;

import javax.annotation.Nullable;

/** Maintains the exact DJ SoundInstance-to-OpenAL-source association. */
public final class OpenALHelper {
    private static final long WAIT_TIMEOUT_NANOS = 30_000_000_000L;

    private static volatile int currentDJSourceId = -1;
    @Nullable
    private static volatile DJSoundInstance currentDJSound;
    @Nullable
    private static volatile Channel currentDJChannel;
    private static volatile boolean waitingForDJSource;
    private static volatile long waitStartNanos;
    private static volatile double lastReportedPositionSeconds;

    private OpenALHelper() {
    }

    public static synchronized void startWaitingForDJSource(DJSoundInstance sound) {
        currentDJSound = sound;
        waitingForDJSource = true;
        waitStartNanos = System.nanoTime();
        currentDJSourceId = -1;
        currentDJChannel = null;
        lastReportedPositionSeconds = 0.0;
        DJCraft.LOGGER.debug("Waiting for DJ source generation={} track={}",
                sound.getGeneration(), sound.getTrackPackId());
    }

    /** Called from NeoForge's exact sound-source event on the sound executor. */
    public static synchronized void onSoundSourceStarted(SoundInstance sound, int sourceId, Channel channel) {
        if (!(sound instanceof DJSoundInstance djSound)) {
            return;
        }
        DJSoundInstance expected = currentDJSound;
        if (!DJAudioBindingPolicy.shouldBind(expected, djSound, waitingForDJSource, djSound.isStopped())) {
            DJCraft.LOGGER.warn("Stopping stale DJ source generation={} track={} source={} expectedGeneration={}",
                    djSound.getGeneration(), djSound.getTrackPackId(), sourceId,
                    expected == null ? -1L : expected.getGeneration());
            channel.stop();
            return;
        }
        if (System.nanoTime() - waitStartNanos >= WAIT_TIMEOUT_NANOS) {
            waitingForDJSource = false;
            DJCraft.LOGGER.warn("Stopping DJ source that completed after timeout generation={} track={} source={}",
                    djSound.getGeneration(), djSound.getTrackPackId(), sourceId);
            channel.stop();
            return;
        }

        long seekPositionMs = djSound.calculateSeekPositionMs(System.nanoTime());
        clearErrors();
        AL10.alSourcef(sourceId, AL11.AL_SEC_OFFSET, seekPositionMs / 1000.0F);
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            waitingForDJSource = false;
            DJCraft.LOGGER.warn("Unable to seek DJ source generation={} track={} source={} error={}",
                    djSound.getGeneration(), djSound.getTrackPackId(), sourceId, error);
            channel.stop();
            return;
        }

        currentDJSourceId = sourceId;
        currentDJChannel = channel;
        waitingForDJSource = false;
        lastReportedPositionSeconds = seekPositionMs / 1000.0;
        DJCraft.LOGGER.info("Bound DJ source generation={} track={} source={} seek={}ms",
                djSound.getGeneration(), djSound.getTrackPackId(), sourceId, seekPositionMs);
    }

    public static synchronized void onSourceStopped(int sourceId, Channel channel) {
        if (DJAudioBindingPolicy.shouldClearBinding(
                currentDJChannel, channel, currentDJSourceId, sourceId)) {
            DJCraft.LOGGER.info("DJ OpenAL source stopped: {}", sourceId);
            currentDJSourceId = -1;
            currentDJChannel = null;
        }
    }

    public static synchronized void onDJSoundStop(DJSoundInstance sound) {
        if (currentDJSound == sound) {
            DJCraft.LOGGER.debug("DJ sound stopped generation={} track={}",
                    sound.getGeneration(), sound.getTrackPackId());
            currentDJSourceId = -1;
            currentDJChannel = null;
            currentDJSound = null;
            waitingForDJSource = false;
            lastReportedPositionSeconds = 0.0;
        }
    }

    public static long getPlaybackPositionMs() {
        float seconds = getPlaybackPositionSeconds();
        return seconds < 0.0F ? -1L : (long) (seconds * 1000.0F);
    }

    public static float getPlaybackPositionSeconds() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return -1.0F;
        }
        try {
            clearErrors();
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                currentDJChannel = null;
                return -1.0F;
            }
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                return -1.0F;
            }
            float seconds = AL10.alGetSourcef(sourceId, AL11.AL_SEC_OFFSET);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                return -1.0F;
            }
            lastReportedPositionSeconds = seconds;
            return seconds;
        } catch (RuntimeException exception) {
            DJCraft.LOGGER.error("Error getting OpenAL playback position", exception);
            return -1.0F;
        }
    }

    public static boolean hasValidDJSource() {
        return currentDJSourceId != -1;
    }

    public static boolean isWaitingForDJSource() {
        return waitingForDJSource;
    }

    public static boolean hasWaitingTimedOut() {
        return waitingForDJSource && System.nanoTime() - waitStartNanos >= WAIT_TIMEOUT_NANOS;
    }

    public static int getCurrentDJSourceId() {
        return currentDJSourceId;
    }

    public static double getLastPositionSeconds() {
        return lastReportedPositionSeconds;
    }

    public static boolean isDJSourcePlaying() {
        return getSourceState() == AL10.AL_PLAYING;
    }

    public static boolean isDJSourcePaused() {
        return getSourceState() == AL10.AL_PAUSED;
    }

    public static boolean isDJSourceStopped() {
        return getSourceState() == AL10.AL_STOPPED;
    }

    private static int getSourceState() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return AL10.AL_NONE;
        }
        try {
            clearErrors();
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                currentDJChannel = null;
                return AL10.AL_NONE;
            }
            return state;
        } catch (RuntimeException exception) {
            return AL10.AL_NONE;
        }
    }

    private static void clearErrors() {
        while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            // Drain errors produced by unrelated sound-engine work before checking our operation.
        }
    }

    public static synchronized void cleanup() {
        currentDJSourceId = -1;
        currentDJChannel = null;
        currentDJSound = null;
        waitingForDJSource = false;
        waitStartNanos = 0L;
        lastReportedPositionSeconds = 0.0;
    }
}

package otto.djgun.djcraft.sound;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import otto.djgun.djcraft.DJCraft;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAL 辅助类
 * 管理 OpenAL source ID 与 DJSoundInstance 的关联
 * 提供高精度的音频播放位置查询
 */
public class OpenALHelper {

    // Source ID -> Channel 的映射（用于追踪所有活动的 source）
    private static final Map<Integer, Channel> activeChannels = new ConcurrentHashMap<>();

    // 当前 DJ 音乐的 OpenAL source ID（-1 表示无效）
    private static volatile int currentDJSourceId = -1;

    // 当前正在播放的 DJ 声音实例
    @Nullable
    private static volatile DJSoundInstance currentDJSound = null;

    // 是否正在等待 DJ 声音的 source 被附加
    private static volatile boolean waitingForDJSource = false;

    // 等待 source 的开始时间（用于超时检测）
    private static volatile long waitStartTimeMs = 0;

    // 等待超时时间（毫秒），长音频全量载入可能需要更长时间
    private static final long WAIT_TIMEOUT_MS = 10000;

    // 仅为了兼容和调试，保存上次播放时间
    private static double lastReportedPositionSeconds = 0.0;

    /**
     * 标记开始等待 DJ 声音的 source（在播放前调用）
     */
    public static void startWaitingForDJSource(DJSoundInstance sound) {
        currentDJSound = sound;
        waitingForDJSource = true;
        waitStartTimeMs = System.currentTimeMillis();
        currentDJSourceId = -1;
        lastReportedPositionSeconds = 0.0;
        // DJCraft.LOGGER.info("Waiting for DJ sound OpenAL source (track: {})",
        // sound.getTrackPackId());
    }

    /**
     * 当 Channel 附加了音频缓冲区时调用（由 ChannelMixin 注入）
     * 在 Sound engine 线程上运行
     */
    public static void onSourceAttached(int sourceId, Channel channel) {
        activeChannels.put(sourceId, channel);

        // 如果正在等待 DJ source，捕获这个 source
        if (waitingForDJSource) {
            // 检查是否超时
            if (System.currentTimeMillis() - waitStartTimeMs < WAIT_TIMEOUT_MS) {
                currentDJSourceId = sourceId;
                waitingForDJSource = false;
                // DJCraft.LOGGER.info("DJ sound captured OpenAL source: {} (track: {})",
                // sourceId, currentDJSound != null ? currentDJSound.getTrackPackId() :
                // "unknown");
            } else {
                waitingForDJSource = false;
                DJCraft.LOGGER.warn("Timeout waiting for DJ source, ignoring source: {}", sourceId);
            }
        } else {
            // DJCraft.LOGGER.debug("OpenAL source attached (non-DJ): {}", sourceId);
        }
    }

    /**
     * 当 Channel 停止时调用（由 ChannelMixin 注入）
     */
    public static void onSourceStopped(int sourceId) {
        activeChannels.remove(sourceId);
        if (sourceId == currentDJSourceId) {
            DJCraft.LOGGER.info("DJ OpenAL source stopped: {}", sourceId);
            currentDJSourceId = -1;
            lastReportedPositionSeconds = 0.0;
        }
    }

    /**
     * 当 DJSoundInstance 停止播放时调用（由 SoundEngineMixin 注入）
     */
    public static void onDJSoundStop(DJSoundInstance sound) {
        if (currentDJSound == sound) {
            DJCraft.LOGGER.info("DJ sound stopped: {}", sound.getTrackPackId());
            currentDJSourceId = -1;
            currentDJSound = null;
            waitingForDJSource = false;
            lastReportedPositionSeconds = 0.0;
        }
    }

    /**
     * 获取当前 DJ 音乐的播放位置（毫秒）
     * 使用 OpenAL 的 AL_SEC_OFFSET 获取高精度时间
     * 
     * @return 播放位置（毫秒），如果没有正在播放的 DJ 音乐则返回 -1
     */
    public static long getPlaybackPositionMs() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return -1;
        }

        try {
            // 清理旧可能积压的 Error
            while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            }

            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                return -1;
            }

            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                return -1;
            }

            // 由于已经将音频从 stream 模式变更为静态模式（一次性载入 Buffer），
            // AL_SEC_OFFSET 返回的值是完美的从歌曲开头到现在的确切音频秒数，永不重置！
            float offsetSeconds = AL10.alGetSourcef(sourceId, AL11.AL_SEC_OFFSET);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                return -1;
            }

            lastReportedPositionSeconds = offsetSeconds;
            return (long) (offsetSeconds * 1000.0);
        } catch (Exception e) {
            DJCraft.LOGGER.error("Error getting OpenAL playback position", e);
            return -1;
        }
    }

    /**
     * 获取当前 DJ 音乐的播放位置（秒，浮点数）
     * 
     * @return 播放位置（秒），如果没有正在播放的 DJ 音乐则返回 -1
     */
    public static float getPlaybackPositionSeconds() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return -1f;
        }

        try {
            while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            }

            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                return -1f;
            }

            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                return -1f;
            }

            float positionSeconds = AL10.alGetSourcef(sourceId, AL11.AL_SEC_OFFSET);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                return -1f;
            }

            return positionSeconds;
        } catch (Exception e) {
            DJCraft.LOGGER.error("Error getting OpenAL playback position", e);
            return -1f;
        }
    }

    /**
     * 检查是否有有效的 DJ OpenAL source
     */
    public static boolean hasValidDJSource() {
        return currentDJSourceId != -1;
    }

    /**
     * 检查是否正在等待 DJ source
     */
    public static boolean isWaitingForDJSource() {
        return waitingForDJSource;
    }

    /**
     * 获取当前 DJ source ID（用于调试）
     */
    public static int getCurrentDJSourceId() {
        return currentDJSourceId;
    }

    /**
     * 获取全局播放位置（秒，用于调试）
     */
    public static double getLastPositionSeconds() {
        return lastReportedPositionSeconds;
    }

    /**
     * 检查 DJ source 是否正在播放
     */
    public static boolean isDJSourcePlaying() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return false;
        }

        try {
            while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            }
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                return false;
            }
            return state == AL10.AL_PLAYING;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 DJ source 是否暂停
     */
    public static boolean isDJSourcePaused() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return false;
        }

        try {
            while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            }
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                return false;
            }
            return state == AL10.AL_PAUSED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 DJ source 是否已明确停止
     * 用于处理静态缓冲加载时长时间处于 AL_INITIAL 的合法等待状态
     */
    public static boolean isDJSourceStopped() {
        int sourceId = currentDJSourceId;
        if (sourceId == -1) {
            return false; // 如果 source 已经被回收或解除绑定，视为未准备或正常关闭，不由该方法管辖
        }

        try {
            while (AL10.alGetError() != AL10.AL_NO_ERROR) {
            }
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (AL10.alGetError() == AL10.AL_INVALID_NAME) {
                currentDJSourceId = -1;
                return false; // 注意：若外部认为丢失也会将其视同停止触发 lostSource，所以这里返回 false 无妨。
            }
            return state == AL10.AL_STOPPED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理所有状态
     */
    public static void cleanup() {
        activeChannels.clear();
        currentDJSourceId = -1;
        currentDJSound = null;
        waitingForDJSource = false;
        lastReportedPositionSeconds = 0.0;
    }
}

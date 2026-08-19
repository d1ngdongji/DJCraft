package otto.djgun.djcraft.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DJ自定义声音实例
 * 用于播放曲目包中的 .ogg 文件
 */
public class DJSoundInstance extends AbstractSoundInstance {

    private static final AtomicLong NEXT_GENERATION = new AtomicLong();

    private final String trackPackId;
    private final long generation;
    private final long initialPositionMs;
    private final long estimatedTransitMs;
    private final long playbackStartMs;
    private final long totalDurationMs;
    private final long createdNanos;
    private volatile boolean stopped = false;

    public DJSoundInstance(ResourceLocation soundLocation, String trackPackId, float volumeMultiplier,
            long initialPositionMs, long estimatedTransitMs, long playbackStartMs, long totalDurationMs) {
        super(soundLocation, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.trackPackId = trackPackId;
        this.generation = NEXT_GENERATION.incrementAndGet();
        this.initialPositionMs = initialPositionMs;
        this.estimatedTransitMs = estimatedTransitMs;
        this.playbackStartMs = playbackStartMs;
        this.totalDurationMs = totalDurationMs;
        this.createdNanos = System.nanoTime();
        this.looping = false;
        this.delay = 0;
        this.volume = volumeMultiplier;
        this.pitch = 1.0F;
        this.relative = true; // 相对于玩家
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.attenuation = Attenuation.NONE;
    }

    /**
     * 停止播放
     */
    public void stopPlaying() {
        this.stopped = true;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    @Override
    public boolean canPlaySound() {
        return !stopped;
    }

    public long calculateSeekPositionMs(long nowNanos) {
        long elapsedMs = Math.max(0L, nowNanos - createdNanos) / 1_000_000L;
        return DJAudioTiming.calculateSeekPositionMs(initialPositionMs, estimatedTransitMs,
                elapsedMs, playbackStartMs, totalDurationMs);
    }

    public long getGeneration() {
        return generation;
    }

    /**
     * 获取曲目包ID
     */
    public String getTrackPackId() {
        return trackPackId;
    }
}

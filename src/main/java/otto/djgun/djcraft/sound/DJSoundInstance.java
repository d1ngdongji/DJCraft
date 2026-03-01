package otto.djgun.djcraft.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import javax.annotation.Nullable; // Added for nullability annotations, though not used in this specific class yet

/**
 * DJ自定义声音实例
 * 用于播放曲目包中的 .ogg 文件
 */
public class DJSoundInstance extends AbstractSoundInstance {

    private final String trackPackId;
    private boolean stopped = false;

    public DJSoundInstance(ResourceLocation soundLocation, String trackPackId, float volumeMultiplier) {
        super(soundLocation, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.trackPackId = trackPackId;
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

    /**
     * 获取曲目包ID
     */
    public String getTrackPackId() {
        return trackPackId;
    }
}

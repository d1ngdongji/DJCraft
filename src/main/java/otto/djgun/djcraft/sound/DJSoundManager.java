package otto.djgun.djcraft.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;

import javax.annotation.Nullable;

/**
 * DJ声音管理器（客户端）
 * 管理曲目音乐的播放
 */
public class DJSoundManager {

    private static DJSoundManager INSTANCE;

    @Nullable
    private DJSoundInstance currentSound;
    @Nullable
    private String currentTrackPackId;

    private DJSoundManager() {
    }

    /**
     * 获取单例实例
     */
    public static DJSoundManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DJSoundManager();
        }
        return INSTANCE;
    }

    /**
     * 播放曲目包的音乐
     * 注意：音频文件需要在资源包中注册，或使用动态声音加载
     */
    public void playTrack(TrackPack trackPack) {
        // 停止当前播放
        stopTrack();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        // 停止之前的背景音乐以及唱片机/音符盒音效
        mc.getSoundManager().stop(null, net.minecraft.sounds.SoundSource.MUSIC);
        mc.getSoundManager().stop(null, net.minecraft.sounds.SoundSource.RECORDS);

        // 构建声音资源位置
        // 格式: djcraft:trackpacks/<packId>/track
        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(
                DJCraft.MODID,
                ("trackpacks." + trackPack.id()).toLowerCase(java.util.Locale.ROOT));

        currentTrackPackId = trackPack.id();

        otto.djgun.djcraft.data.TrackSettings settings = trackPack.settings();
        float vol = settings != null && settings.volumeMultiplier() != null ? settings.volumeMultiplier() : 1.0f;

        DJSoundInstance sound = new DJSoundInstance(soundLocation, trackPack.id(), vol);
        currentSound = sound;

        // 标记开始等待 OpenAL source 附加
        OpenALHelper.startWaitingForDJSource(sound);

        // 播放声音
        mc.getSoundManager().play(sound);

        DJCraft.LOGGER.info("Playing track: {} (sound: {})", trackPack.id(), soundLocation);
    }

    /**
     * 停止当前播放
     */
    public void stopTrack() {
        DJSoundInstance sound = currentSound;
        if (sound != null) {
            sound.stopPlaying();
            if (Minecraft.getInstance().getSoundManager().isActive(sound)) {
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
            DJCraft.LOGGER.info("Stopped track: {}", currentTrackPackId);
        }
        currentSound = null;
        currentTrackPackId = null;
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return currentSound != null && !currentSound.isStopped();
    }

    /**
     * 获取当前播放的曲目包ID
     */
    @Nullable
    public String getCurrentTrackPackId() {
        return currentTrackPackId;
    }
}

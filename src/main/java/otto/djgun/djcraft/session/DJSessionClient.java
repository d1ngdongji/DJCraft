package otto.djgun.djcraft.session;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.event.OnBeatEvent;
import otto.djgun.djcraft.sound.OpenALHelper;

import java.util.List;

/**
 * 客户端 DJ 播放会话
 * 使用 OpenAL 获取高精度播放时间来进行节拍检测
 */
@OnlyIn(Dist.CLIENT)
public class DJSessionClient {

    private final TrackPack trackPack;
    private int lastBeatIndex = -1;
    private boolean playing = true;

    // 是否使用 OpenAL 时间（如果 OpenAL 不可用则回退到系统时间）
    private boolean useOpenALTime = true;

    // 暂停状态追踪
    private boolean paused = false;

    // 上次有效的 OpenAL 原始时间（用于检测异常跳变）
    private long lastValidOpenALRawTimeMs = 0;

    public DJSessionClient(TrackPack trackPack) {
        this.trackPack = trackPack;
        DJCraft.LOGGER.info("DJSessionClient created for track: {}", trackPack.id());
    }

    /**
     * 开始会话（在音乐开始播放后调用）
     */
    public void start() {
        this.lastBeatIndex = -1;
        this.playing = true;
        this.lastValidOpenALRawTimeMs = 0;
        this.paused = false;

        // 默认尝试使用 OpenAL
        this.useOpenALTime = true;

        // 即使现在 OpenAL 还没就绪（可能在等待异步加载），我们也保持 useOpenALTime = true
        // 这样一旦 source ready，就会立即使用 OpenAL 时间
        if (OpenALHelper.hasValidDJSource()) {
            DJCraft.LOGGER.info("DJSessionClient started with OpenAL timing (source: {})",
                    OpenALHelper.getCurrentDJSourceId());
        } else {
            DJCraft.LOGGER.info("DJSessionClient started, waiting for OpenAL source...");
        }
    }

    /**
     * 设置暂停状态
     */
    public void setPaused(boolean paused) {
        if (this.paused == paused)
            return;
        this.paused = paused;
        // 暂时无需记录时间，因为我们完全依赖 OpenAL
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * 获取原始播放时间（毫秒，不含 offset 修正）
     */
    private long getRawCurrentTimeMs() {
        // 始终尝试检查是否有有效的 OpenAL source
        if (useOpenALTime && OpenALHelper.hasValidDJSource()) {
            long openALTime = OpenALHelper.getPlaybackPositionMs();
            if (openALTime >= 0) {
                // 检测时间跳变（如果时间向后跳了超过 100ms，可能是异常，但在循环播放或seek时可能合法，这里仅警告）
                if (openALTime < lastValidOpenALRawTimeMs - 100 && lastValidOpenALRawTimeMs > 0) {
                    DJCraft.LOGGER.warn("OpenAL time jumped backward: {} -> {}",
                            lastValidOpenALRawTimeMs, openALTime);
                }
                lastValidOpenALRawTimeMs = openALTime;
                return openALTime;
            }
        }

        // 如果 OpenAL 还没就绪或失败，返回上次已知的有效时间（即时间冻结）
        // 不回退到系统时间
        return lastValidOpenALRawTimeMs;
    }

    private long toTimelineTimeMs(long rawTimeMs) {
        long adjusted = rawTimeMs - trackPack.getOffsetMs();
        return Math.max(0, adjusted);
    }

    /**
     * 获取时间轴时间（毫秒，已应用 TrackMeta.offsetMs 修正）
     */
    public long getCurrentTimeMs() {
        return toTimelineTimeMs(getRawCurrentTimeMs());
    }

    /**
     * 每帧更新，检测节拍并发送事件
     * 应该在客户端 tick 中调用
     */
    public void tick() {
        if (!playing)
            return;

        long rawCurrentTime = getRawCurrentTimeMs();
        long currentTime = toTimelineTimeMs(rawCurrentTime);

        // 检测经过的节拍
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty())
            return;

        // 查找当前应该触发的节拍
        for (int i = lastBeatIndex + 1; i < combatLine.size(); i++) {
            BeatEvent beat = combatLine.get(i);
            if (beat.t() <= currentTime) {
                // 触发节拍事件
                fireBeatEvent(beat, i, currentTime);
                lastBeatIndex = i;
            } else {
                break;
            }
        }
    }

    /**
     * 发送节拍事件
     */
    private void fireBeatEvent(BeatEvent beat, int index, long currentTime) {
        BeatDefinition definition = trackPack.getDefinition(beat.type());
        if (definition == null) {
            definition = BeatDefinition.createDefault();
        }

        // 客户端使用本地玩家
        var player = Minecraft.getInstance().player;
        if (player == null)
            return;

        if (Config.ENABLE_DEBUG_SOUND.get()) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.0F, false);
        }

        OnBeatEvent event = new OnBeatEvent(player, beat, definition, currentTime, index);
        NeoForge.EVENT_BUS.post(event);

        // Debug日志（显示时间差异）
        long timeDiff = currentTime - beat.t();
        DJCraft.LOGGER.debug("Client Beat #{} at t={}ms (actual: {}ms, diff: {}ms), type={}",
                index, beat.t(), currentTime, timeDiff, beat.type());
    }

    /**
     * 获取下一个节拍
     */
    public BeatEvent getNextBeat() {
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty())
            return null;

        int nextIndex = lastBeatIndex + 1;
        if (nextIndex < combatLine.size()) {
            return combatLine.get(nextIndex);
        }
        return null;
    }

    /**
     * 获取上一个节拍
     */
    public BeatEvent getPreviousBeat() {
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty())
            return null;

        if (lastBeatIndex >= 0 && lastBeatIndex < combatLine.size()) {
            return combatLine.get(lastBeatIndex);
        }
        return null;
    }

    /**
     * 获取距下一个节拍的毫秒数
     */
    public long getMsToNextBeat() {
        BeatEvent next = getNextBeat();
        if (next == null)
            return -1;
        return next.t() - getCurrentTimeMs();
    }

    /**
     * 获取距上一个节拍的毫秒数
     */
    public long getMsSincePreviousBeat() {
        BeatEvent prev = getPreviousBeat();
        if (prev == null)
            return -1;
        return getCurrentTimeMs() - prev.t();
    }

    /**
     * 停止会话
     */
    public void stop() {
        this.playing = false;
        DJCraft.LOGGER.info("DJSessionClient stopped");
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * 获取曲目包
     */
    public TrackPack getTrackPack() {
        return trackPack;
    }

    /**
     * 是否正在使用 OpenAL 时间
     */
    public boolean isUsingOpenALTime() {
        return useOpenALTime && OpenALHelper.hasValidDJSource();
    }

    /**
     * 获取已触发的节拍数量
     */
    public int getTriggeredBeatCount() {
        return lastBeatIndex + 1;
    }

    /**
     * 获取最后一个已触发节拍的索引 (用于判定器精确定位)
     */
    public int getLastBeatIndex() {
        return lastBeatIndex;
    }

    /**
     * 获取总节拍数量
     */
    public int getTotalBeatCount() {
        return trackPack.getCombatBeatCount();
    }

    /**
     * 计算经过指定节拍数后的时间戳（相对于曲目开始）
     */
    public long getCompletionTimeMs(double beats) {
        return otto.djgun.djcraft.util.BeatGridUtil.calculateTargetTime(getCurrentTimeMs(),
                trackPack.timeline().combatLine(), beats);
    }

    /**
     * 获取 OpenAL source ID（用于调试）
     */
    public int getOpenALSourceId() {
        return OpenALHelper.getCurrentDJSourceId();
    }
}

package otto.djgun.djcraft.session;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.event.OnBeatEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DJ播放会话
 * 管理单个曲目的播放状态和节拍检测
 */
public class DJSession {

    private final Player player;
    private final TrackPack trackPack;
    private final long startTimeNs;
    private int lastBeatIndex = -1;
    private boolean playing = true;
    private long totalPausedTimeNs = 0;
    private long pauseStartTimeNs = -1;

    public DJSession(Player player, TrackPack trackPack) {
        this.player = player;
        this.trackPack = trackPack;
        this.startTimeNs = System.nanoTime();

        DJCraft.LOGGER.info("DJSession started for {} with track {}",
                player.getName().getString(), trackPack.id());

        updateAttackSpeed(true);
    }

    /**
     * 获取原始播放时间（毫秒，不含 offset 修正）
     */
    private long getRawCurrentTimeMs() {
        long nowNs = System.nanoTime();
        long elapsedNs;
        if (pauseStartTimeNs != -1) {
            elapsedNs = pauseStartTimeNs - startTimeNs - totalPausedTimeNs;
        } else {
            elapsedNs = nowNs - startTimeNs - totalPausedTimeNs;
        }
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, elapsedNs));
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
     * 设置暂停状态
     */
    public void setPaused(boolean paused) {
        long nowNs = System.nanoTime();

        if (paused && pauseStartTimeNs == -1) {
            pauseStartTimeNs = nowNs;
            DJCraft.LOGGER.debug("DJSession paused");
        } else if (!paused && pauseStartTimeNs != -1) {
            totalPausedTimeNs += (nowNs - pauseStartTimeNs);
            pauseStartTimeNs = -1;
            DJCraft.LOGGER.debug("DJSession resumed, total paused: {}ms",
                    TimeUnit.NANOSECONDS.toMillis(totalPausedTimeNs));
        }
    }

    /**
     * 是否处于暂停状态
     */
    public boolean isPaused() {
        return pauseStartTimeNs != -1;
    }

    /**
     * 每tick更新，检测节拍并发送事件
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

        OnBeatEvent event = new OnBeatEvent(player, beat, definition, currentTime, index);
        NeoForge.EVENT_BUS.post(event);

        // Debug日志
        DJCraft.LOGGER.debug("Beat #{} at t={}ms, type={}", index, beat.t(), beat.type());
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
     * 停止播放
     */
    public void stop() {
        if (!this.playing)
            return;
        this.playing = false;
        DJCraft.LOGGER.info("DJSession stopped for {}", player.getName().getString());
        updateAttackSpeed(false);
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
     * 获取玩家
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取已触发的节拍数量
     */
    public int getTriggeredBeatCount() {
        return lastBeatIndex + 1;
    }

    /**
     * 获取最后一个已触发节拍的索引（用于判定器）
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

    // 定义高攻速修饰符ID
    private static final net.minecraft.resources.ResourceLocation DJ_ATTACK_SPEED_ID = net.minecraft.resources.ResourceLocation
            .fromNamespaceAndPath(DJCraft.MODID, "dj_mode_speed");

    // 应用/移除攻速修饰符
    private void updateAttackSpeed(boolean enable) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = player
                .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);

        if (instance != null) {
            if (enable) {
                if (!instance.hasModifier(DJ_ATTACK_SPEED_ID)) {
                    instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            DJ_ATTACK_SPEED_ID, 100.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                instance.removeModifier(DJ_ATTACK_SPEED_ID);
            }
        }
    }
}

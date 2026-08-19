package otto.djgun.djcraft.session;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.init.ModAttributes;
import otto.djgun.djcraft.init.ModGameRules;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.network.packet.ClientPlaybackReadyPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.sound.DJSoundManager;
import otto.djgun.djcraft.sound.OpenALHelper;
import otto.djgun.djcraft.client.playback.DJPlaybackController;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * 客户端 DJ 模式管理器
 * 管理客户端的 DJ 会话，使用 OpenAL 高精度时间
 */
@OnlyIn(Dist.CLIENT)
public class DJModeManagerClient {

    private static final long AUDIO_STALL_TIMEOUT_NANOS = 1_000_000_000L;

    private static DJModeManagerClient INSTANCE;

    @Nullable
    private DJSessionClient currentSession;

    @Nullable
    private String currentTrackPackId;
    @Nullable
    private UUID currentDiscId;
    private int retainedCombo;
    private double retainedEnergy;
    private double retainedMaxEnergy = ModAttributes.DEFAULT_MAX_ENERGY;
    private int retainedToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
    private int retainedMaxToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
    private int retainedOffBeatAttackDamagePercent;
    private long lastObservedPlaybackPositionMs = -1L;
    private long lastPlaybackProgressNanos;
    private boolean audioSourceSynchronized;

    private DJModeManagerClient() {
    }

    /**
     * 获取单例实例
     */
    public static DJModeManagerClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DJModeManagerClient();
        }
        return INSTANCE;
    }

    /**
     * 开始播放曲目（由网络包触发）
     */
    public boolean startSession(long sessionId, String trackPackId, UUID discId) {
        return startSession(sessionId, trackPackId, discId, 0L);
    }

    public boolean startSession(long sessionId, String trackPackId, UUID discId, long initialPositionMs) {
        return startSession(sessionId, trackPackId, discId, initialPositionMs, 0L);
    }

    public boolean startSession(long sessionId, String trackPackId, UUID discId, long initialPositionMs,
            long estimatedTransitMs) {
        if (!otto.djgun.djcraft.client.ClientTrackRegistry.getInstance().isVerified(trackPackId)) {
            DJCraft.LOGGER.error("Refusing unverified TrackPack for client session: {}", trackPackId);
            return false;
        }
        // 停止当前会话
        stopSession();

        // 获取曲目包
        Optional<TrackPack> packOpt = TrackPackManager.getInstance().getTrackPack(trackPackId);
        if (packOpt.isEmpty()) {
            DJCraft.LOGGER.error("TrackPack not found for client session: {}", trackPackId);
            return false;
        }

        TrackPack pack = packOpt.get();
        long effectiveInitialPositionMs = Math.max(initialPositionMs, pack.getPlaybackStartMs());

        // 播放音乐（这会触发 Mixin 捕获 OpenAL source ID）
        DJSoundManager.getInstance().playTrack(pack, effectiveInitialPositionMs, estimatedTransitMs);

        // 创建客户端会话
        DJSessionClient session = new DJSessionClient(sessionId, pack);
        currentSession = session;
        currentTrackPackId = trackPackId;
        currentDiscId = discId;

        // 立即启动会话（OpenAL source 应该已经被 Mixin 捕获）
        session.start(effectiveInitialPositionMs);
        resetAudioProgressWatchdog();
        audioSourceSynchronized = false;
        session.applyResourceState(sessionId, retainedCombo, 0, retainedEnergy, retainedMaxEnergy,
                retainedToleranceChances, retainedMaxToleranceChances,
                retainedOffBeatAttackDamagePercent);

        DJCraft.LOGGER.info("Client DJ session started for: {} (OpenAL source: {})",
                trackPackId, OpenALHelper.getCurrentDJSourceId());
        return true;
    }

    /**
     * 停止当前会话
     */
    public void stopSession() {
        if (currentSession != null) {
            retainedCombo = currentSession.getCombo();
            retainedEnergy = currentSession.getEnergy();
            retainedMaxEnergy = currentSession.getMaxEnergy();
            retainedToleranceChances = currentSession.getToleranceChances();
            retainedMaxToleranceChances = currentSession.getMaxToleranceChances();
            retainedOffBeatAttackDamagePercent = currentSession.getOffBeatAttackDamagePercent();
            currentSession.stop();
            currentSession = null;
        }
        currentTrackPackId = null;
        currentDiscId = null;
        resetAudioProgressWatchdog();
        audioSourceSynchronized = false;

        // 停止音乐
        DJSoundManager.getInstance().stopTrack();

        // 清理 OpenAL 状态
        OpenALHelper.cleanup();
    }

    public void reset() {
        stopSession();
        retainedCombo = 0;
        retainedEnergy = 0.0;
        retainedMaxEnergy = ModAttributes.DEFAULT_MAX_ENERGY;
        retainedToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
        retainedMaxToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
        retainedOffBeatAttackDamagePercent = 0;
    }

    @Nullable
    public UUID getCurrentDiscId() {
        return currentDiscId;
    }

    /**
     * 游戏 Tick 更新（20 TPS）
     * 仅负责 OpenAL source 存活性检测和会话结束拦截。
     * beat 检测已移到 renderTick()（每渲染帧执行）以提高精度。
     */
    public void gameTick() {
        DJPlaybackController.getInstance().tick();
        DJSessionClient session = currentSession;
        if (session == null)
            return;

        if (!session.isPlaying()) {
            return;
        }

        // 同步暂停状态
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean wasPaused = session.isPaused();
        session.setPaused(mc.isPaused());
        if (wasPaused && !session.isPaused()) {
            // The integrated server's nano-time clock includes time spent in the
            // singleplayer pause menu, while OpenAL correctly remains frozen.
            // Re-send the audio clock before any subsequent combat request.
            session.markPlaybackReadyPending();
            resetAudioProgressWatchdog();
        }
        if (session.isPaused()) {
            resetAudioProgressWatchdog();
        }

        synchronizeAttachedSource(session);

        if (!session.isPaused() && !session.isPlaybackReadySent() && OpenALHelper.hasValidDJSource()) {
            PacketDistributor.sendToServer(
                    new ClientPlaybackReadyPayload(session.getSessionId(), session.getCurrentTimeMs()));
            session.markPlaybackReadySent();
        }

        if (session.hasReachedPlaybackEnd()) {
            DJCraft.LOGGER.info("Configured TrackPack playback end reached, auto-closing DJ session.");
            handleNaturalEnd(session);
            return;
        }

        // 正常暂停菜单会同时暂停会话，不属于音频异常。其他情况下，玩家把主音量或唱片
        // 音量关到 0、OpenAL source 被暂停/停止，或 source 意外丢失，都必须立即退出，
        // 不能继续复用冻结在判定容差内的最后播放时间。
        boolean soundDisabled = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) <= 0.0F
                || mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS) <= 0.0F;
        boolean lostSource = !OpenALHelper.hasValidDJSource() && !OpenALHelper.isWaitingForDJSource();
        boolean sourcePaused = OpenALHelper.hasValidDJSource() && OpenALHelper.isDJSourcePaused();
        boolean sourceStopped = OpenALHelper.hasValidDJSource() && OpenALHelper.isDJSourceStopped();
        boolean sourceStalled = !session.isPaused() && OpenALHelper.isDJSourcePlaying()
                && isAudioClockStalled(session);
        boolean sourceWaitTimedOut = OpenALHelper.hasWaitingTimedOut();

        if (!session.isPaused() && (lostSource || sourceStopped)
                && session.isAtOrNearPlaybackEnd(250L)) {
            DJCraft.LOGGER.info("DJ source ended within the configured tail tolerance; treating as natural end");
            handleNaturalEnd(session);
            return;
        }

        if (!session.isPaused() && (soundDisabled || lostSource || sourcePaused || sourceStopped
                || sourceStalled || sourceWaitTimedOut)) {
            DJCraft.LOGGER.warn("DJ audio became unavailable "
                    + "(disabled={}, lost={}, paused={}, stopped={}, stalled={}, waitTimeout={})",
                    soundDisabled, lostSource, sourcePaused, sourceStopped, sourceStalled, sourceWaitTimedOut);
            handleAudioUnavailable(session);
            return;
        }
    }

    /**
     * 渲染帧 Tick 更新（每渲染帧，~60fps）
     * 负责 beat 检测和触发，利用高频率检查降低最大 beat 延迟。
     */
    public void renderTick() {
        DJSessionClient session = currentSession;
        if (session == null)
            return;

        if (!session.isPlaying()) {
            return;
        }

        // 暂停时不进行 beat 检测
        if (session.isPaused()) {
            return;
        }

        synchronizeAttachedSource(session);

        if (session.hasReachedPlaybackEnd()) {
            DJCraft.LOGGER.info("Configured TrackPack playback end reached, stopping audio.");
            handleNaturalEnd(session);
            return;
        }

        // 更新 beat 检测
        session.tick();
    }

    private void handleNaturalEnd(DJSessionClient session) {
        if (currentSession != session) {
            return;
        }
        long endedSessionId = session.getSessionId();
        if (otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance()
                .onConfiguredPlaybackEnd(endedSessionId)) {
            stopSession();
            return;
        }
        PacketDistributor.sendToServer(new ClientStopSessionPayload(endedSessionId, StopReason.AUDIO_ENDED));
        stopSession();
        DJPlaybackController.getInstance().onNaturalEnd(endedSessionId);
    }

    private void handleAudioUnavailable(DJSessionClient session) {
        if (currentSession != session) {
            return;
        }
        long sessionId = session.getSessionId();
        var groupClient = otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance();
        long groupPlaybackId = groupClient.onAudioUnavailable(sessionId);
        PacketDistributor.sendToServer(new ClientStopSessionPayload(
                sessionId, groupPlaybackId, StopReason.AUDIO_UNAVAILABLE));
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(groupPlaybackId == 0L
                            ? "message.djcraft.audio_unavailable"
                            : "message.djcraft.group.audio_recovering"), false);
        }
        stopSession();
        if (groupPlaybackId == 0L) {
            DJPlaybackController.getInstance().onStopped(sessionId);
        }
    }

    private boolean isAudioClockStalled(DJSessionClient session) {
        long playbackPositionMs = session.getPlaybackTimeMs();
        long nowNanos = System.nanoTime();
        if (playbackPositionMs != lastObservedPlaybackPositionMs) {
            lastObservedPlaybackPositionMs = playbackPositionMs;
            lastPlaybackProgressNanos = nowNanos;
            return false;
        }
        if (lastPlaybackProgressNanos == 0L) {
            lastPlaybackProgressNanos = nowNanos;
            return false;
        }
        return nowNanos - lastPlaybackProgressNanos >= AUDIO_STALL_TIMEOUT_NANOS;
    }

    private void synchronizeAttachedSource(DJSessionClient session) {
        if (audioSourceSynchronized || !OpenALHelper.hasValidDJSource()) {
            return;
        }
        long attachedPositionMs = OpenALHelper.getPlaybackPositionMs();
        if (attachedPositionMs >= 0L) {
            session.alignToPlaybackPosition(attachedPositionMs);
            audioSourceSynchronized = true;
            resetAudioProgressWatchdog();
        }
    }

    private void resetAudioProgressWatchdog() {
        lastObservedPlaybackPositionMs = -1L;
        lastPlaybackProgressNanos = 0L;
    }

    /**
     * @deprecated 请使用 gameTick() + renderTick()
     */
    @Deprecated
    public void tick() {
        gameTick();
        renderTick();
    }

    /**
     * 获取当前会话
     */
    public Optional<DJSessionClient> getSession() {
        return Optional.ofNullable(currentSession);
    }

    /**
     * 获取当前活跃（正在播放）的 DJ 会话。
     * <p>
     * 相比 {@link #getSession()} + {@link #isInDJMode()} 组合调用，
     * 此方法将"在 DJ 模式中且 session 存在且正在播放"三重判断合并为单次调用，
     * 避免调用方重复实现相同逻辑。
     *
     * @return 正在播放的 {@link DJSessionClient}，否则 {@link Optional#empty()}
     */
    public Optional<DJSessionClient> getActiveSession() {
        return isInDJMode() ? Optional.of(currentSession) : Optional.empty();
    }

    /**
     * 是否在 DJ 模式中
     */
    public boolean isInDJMode() {
        return currentSession != null && currentSession.isPlaying();
    }

    /**
     * 获取当前曲目包ID
     */
    @Nullable
    public String getCurrentTrackPackId() {
        return currentTrackPackId;
    }
}

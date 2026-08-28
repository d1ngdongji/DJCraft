package otto.djgun.djcraft.session;

import net.minecraft.world.entity.player.Player;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.network.packet.StopTrackPayload;
import otto.djgun.djcraft.item.DJFumoItem;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import otto.djgun.djcraft.data.DiscStatistics;

/**
 * DJ模式管理器
 * 管理所有玩家的DJ模式状态（单例）
 */
public class DJModeManager {

    private static DJModeManager INSTANCE;

    private final Map<UUID, DJSession> activeSessions = new HashMap<>();
    private List<DJSession> activeSessionSnapshot = List.of();
    private final Map<UUID, RetainedResources> retainedResources = new HashMap<>();

    private DJModeManager() {
    }

    /**
     * 获取单例实例
     */
    public static DJModeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DJModeManager();
        }
        return INSTANCE;
    }

    /**
     * 为玩家开始DJ会话
     */
    public DJSession startSession(Player player, TrackPack trackPack) {
        return startSession(player, trackPack, null, DiscStatistics.EMPTY);
    }

    public DJSession startSession(Player player, TrackPack trackPack, UUID discId, DiscStatistics discStatistics) {
        long playbackStartMs = trackPack.getPlaybackStartMs();
        return startSession(player, trackPack, discId, discStatistics,
                new StandaloneDJServerClock(playbackStartMs), playbackStartMs > 0L);
    }

    public DJSession startSession(Player player, TrackPack trackPack, UUID discId, DiscStatistics discStatistics,
            DJServerClock clock, boolean skipElapsedBeats) {
        // 停止已有会话
        stopSession(player);

        long sessionId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        RetainedResources retained = retainedResources.get(player.getUUID());
        int toleranceBonus = ModItems.DJFUMO.get().isActiveFor(player)
                ? DJFumoItem.TOLERANCE_CHANCE_BONUS : 0;
        int maxToleranceChances = otto.djgun.djcraft.init.ModGameRules.maxToleranceChances(
                player.level().getGameRules(), toleranceBonus);
        double initialEnergy = retained == null ? 0.0 : retained.energy();
        int initialCombo = retained == null ? 0 : retained.combo();
        int initialToleranceChances = retained == null ? maxToleranceChances : retained.toleranceChances();
        int initialToleranceRechargeProgress = retained == null ? 0 : retained.toleranceRechargeProgress();
        DJSession session = new DJSession(sessionId, player, trackPack, initialCombo, initialEnergy,
                initialToleranceChances, initialToleranceRechargeProgress, discId, discStatistics,
                clock, skipElapsedBeats);
        activeSessions.put(player.getUUID(), session);
        refreshActiveSessionSnapshot();

        DJCraft.LOGGER.info("Started DJ session for {} with track {}",
                player.getName().getString(), trackPack.id());

        return session;
    }

    /**
     * 停止玩家的DJ会话
     */
    public void stopSession(Player player) {
        stopSession(player, StopReason.REQUESTED);
    }

    public void stopSession(Player player, StopReason reason) {
        DJSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            refreshActiveSessionSnapshot();
            session.stop();
            retain(player.getUUID(), session);
            // 直接套用 /dj stop 的方法：任何原因导致服务端停止会话，都强行向客户端下发 StopTrack 负载，确保毫无残留
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new StopTrackPayload(session.getSessionId(), reason));
                if (reason == StopReason.CLOCK_DESYNC) {
                    DJNetworkGroupManager.getInstance().onMemberClockDesync(
                            serverPlayer, session.getSessionId());
                }
            }
        }
    }

    /**
     * 获取玩家的DJ会话
     */
    public Optional<DJSession> getSession(Player player) {
        return Optional.ofNullable(activeSessions.get(player.getUUID()));
    }

    /**
     * 检查玩家是否在DJ模式中
     */
    public boolean isInDJMode(Player player) {
        DJSession session = activeSessions.get(player.getUUID());
        return session != null && session.isPlaying();
    }

    /**
     * 更新所有活跃会话（每tick调用）
     */
    public void tick() {
        // OnBeatEvent listeners may synchronously start or stop sessions. Iterate the
        // immutable membership snapshot and conditionally remove only the same session.
        List<DJSession> sessions = activeSessionSnapshot;
        for (DJSession session : sessions) {
            UUID playerId = session.getPlayer().getUUID();
            if (activeSessions.get(playerId) != session) {
                continue;
            }

            if (session.isPlaying()) {
                session.tick();
            }
            if (!session.isPlaying() && activeSessions.remove(playerId, session)) {
                refreshActiveSessionSnapshot();
                retain(playerId, session);
                // 会话自然结束，统一下发停止指令给客户端，确保绝对同步
                if (session.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                            new StopTrackPayload(session.getSessionId(), StopReason.AUDIO_ENDED));
                }
            }
        }
    }

    /**
     * 获取所有活跃会话
     */
    public Collection<DJSession> getActiveSessions() {
        return activeSessionSnapshot;
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 停止所有会话
     */
    public void stopAllSessions() {
        List<DJSession> sessions = activeSessionSnapshot;
        activeSessions.clear();
        refreshActiveSessionSnapshot();
        for (DJSession session : sessions) {
            session.stop();
            if (session.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new StopTrackPayload(session.getSessionId(), StopReason.SERVER_STOP));
            }
        }
        retainedResources.clear();
    }

    private void refreshActiveSessionSnapshot() {
        activeSessionSnapshot = List.copyOf(activeSessions.values());
    }

    private void retain(UUID playerId, DJSession session) {
        retainedResources.put(playerId, new RetainedResources(session.getCombo(), session.getEnergy(),
                session.getToleranceChances(), session.getToleranceRechargeProgress()));
    }

    private record RetainedResources(int combo, double energy, int toleranceChances,
            int toleranceRechargeProgress) {
    }
}

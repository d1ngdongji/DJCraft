package otto.djgun.djcraft.session;

import net.minecraft.world.entity.player.Player;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;

import java.util.*;

/**
 * DJ模式管理器
 * 管理所有玩家的DJ模式状态（单例）
 */
public class DJModeManager {

    private static DJModeManager INSTANCE;

    private final Map<UUID, DJSession> activeSessions = new HashMap<>();

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
        // 停止已有会话
        stopSession(player);

        DJSession session = new DJSession(player, trackPack);
        activeSessions.put(player.getUUID(), session);

        DJCraft.LOGGER.info("Started DJ session for {} with track {}",
                player.getName().getString(), trackPack.id());

        return session;
    }

    /**
     * 停止玩家的DJ会话
     */
    public void stopSession(Player player) {
        DJSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            session.stop();
            // 直接套用 /dj stop 的方法：任何原因导致服务端停止会话，都强行向客户端下发 StopTrack 负载，确保毫无残留
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new otto.djgun.djcraft.network.packet.StopTrackPayload());
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
        // 使用迭代器安全地移除已结束的会话
        Iterator<Map.Entry<UUID, DJSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DJSession> entry = it.next();
            DJSession session = entry.getValue();

            if (session.isPlaying()) {
                session.tick();
            } else {
                it.remove();
                // 会话自然结束，统一下发停止指令给客户端，确保绝对同步
                if (session.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                            new otto.djgun.djcraft.network.packet.StopTrackPayload());
                }
            }
        }
    }

    /**
     * 获取所有活跃会话
     */
    public Collection<DJSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
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
        for (DJSession session : activeSessions.values()) {
            session.stop();
            if (session.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new otto.djgun.djcraft.network.packet.StopTrackPayload());
            }
        }
        activeSessions.clear();
    }
}

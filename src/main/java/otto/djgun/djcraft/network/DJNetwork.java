package otto.djgun.djcraft.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.combat.DJCombatHandler;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.ReloadTracksPayload;
import otto.djgun.djcraft.network.packet.StopTrackPayload;
import otto.djgun.djcraft.network.packet.SyncTrackHashesPayload;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJModeManagerClient;

/**
 * DJ网络通信管理
 */
@EventBusSubscriber(modid = DJCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DJNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(DJCraft.MODID).versioned("1.0.0");

        // 注册 S2C 数据包
        registrar.playToClient(
                PlayTrackPayload.TYPE,
                PlayTrackPayload.CODEC,
                DJNetwork::handlePlayTrack);

        registrar.playToClient(
                StopTrackPayload.TYPE,
                StopTrackPayload.CODEC,
                DJNetwork::handleStopTrack);

        registrar.playToClient(
                ReloadTracksPayload.TYPE,
                ReloadTracksPayload.CODEC,
                DJNetwork::handleReloadTracks);

        registrar.playToClient(
                SyncTrackHashesPayload.TYPE,
                SyncTrackHashesPayload.CODEC,
                DJNetwork::handleSyncTrackHashes);

        // 注册 C2S 数据包
        registrar.playToServer(
                DJAttackClientPayload.TYPE,
                DJAttackClientPayload.CODEC,
                DJNetwork::handleClientDJAttack);

        registrar.playToServer(
                ClientStopSessionPayload.TYPE,
                ClientStopSessionPayload.CODEC,
                DJNetwork::handleClientStopSession);
    }

    /**
     * 处理客户端节拍判定数据包 (服务端执行)
     * 将客户端用 OpenAL 精确时钟做出的判定结果存入 DJCombatHandler，
     * 伤害管道事件 (LivingIncomingDamageEvent) 会从这里取出结果直接使用，不再重复判定。
     */
    private static void handleClientDJAttack(final DJAttackClientPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player == null)
                return;

            // 简单合法性校验：玩家必须处于活跃 DJ 会话中
            var optSession = DJModeManager.getInstance().getSession(player);
            if (optSession.isEmpty() || !optSession.get().isPlaying()) {
                DJCraft.LOGGER.debug("DJ attack packet from {} but no active session, ignoring",
                        player.getName().getString());
                return;
            }

            // 将客户端判定结果存入 pending map
            // DJCombatHandler.onLivingIncomingDamage 会在下次攻击触发时取出
            HitResult result = new HitResult(payload.isHit(), payload.damageModifier(), null, null);
            DJCombatHandler.receivePendingJudgment(player.getUUID(), result);
            DJCraft.LOGGER.debug("DJ judgment received from client {}: isHit={}, dmgRate={}",
                    player.getName().getString(), payload.isHit(), payload.damageModifier());
        });
    }

    /**
     * 处理客户端主动停止会话数据包 (服务端执行)
     * 当客户端检测到 OpenAL source 意外停止时发送，
     * 服务端收到后立即终止该玩家的 DJ 会话，防止继续按 DJ 模式处理攻击。
     */
    private static void handleClientStopSession(final ClientStopSessionPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player == null)
                return;

            boolean wasActive = DJModeManager.getInstance().isInDJMode(player);
            DJModeManager.getInstance().stopSession(player);

            if (wasActive) {
                DJCraft.LOGGER.info("Server DJ session stopped by client request (OpenAL stopped) for: {}",
                        player.getName().getString());
            }
        });
    }

    /**
     * 处理刷新数据包 (客户端执行)
     */
    private static void handleReloadTracks(final ReloadTracksPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 1. 刷新客户端曲目缓存
            TrackPackManager.getInstance().reloadAllPacks();

            // 2. 重新验证双端曲目列表
            ClientTrackRegistry.getInstance().revalidate();

            // 3. 刷新 Minecraft 资源系统 (这是为了重新加载动态 sounds.json)
            net.minecraft.client.Minecraft.getInstance().reloadResourcePacks();

            DJCraft.LOGGER.info("Client reloaded TrackPacks and Resources");
        });
    }

    /**
     * 处理哈希同步包 (客户端执行)
     */
    private static void handleSyncTrackHashes(final SyncTrackHashesPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTrackRegistry.getInstance().onReceiveServerHashes(payload.packHashes());
            DJCraft.LOGGER.info("Received server track hashes: {} packs", payload.packHashes().size());
        });
    }

    /**
     * 处理播放数据包 (客户端执行)
     * 使用 DJModeManagerClient 和 OpenAL 高精度时间
     */
    private static void handlePlayTrack(final PlayTrackPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            String trackId = payload.trackId();

            // 使用客户端管理器启动会话（内部会处理音乐播放和 OpenAL 时间）
            DJModeManagerClient.getInstance().startSession(trackId);

            DJCraft.LOGGER.info("Client received PlayTrack: {}", trackId);
        });
    }

    /**
     * 处理停止数据包 (客户端执行)
     */
    private static void handleStopTrack(final StopTrackPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 使用客户端管理器停止会话
            DJModeManagerClient.getInstance().stopSession();

            DJCraft.LOGGER.info("Client received StopTrack");
        });
    }
}
